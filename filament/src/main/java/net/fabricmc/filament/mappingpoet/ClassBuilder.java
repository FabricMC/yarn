/*
 * Copyright (c) 2020 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.filament.mappingpoet;

import static net.fabricmc.filament.mappingpoet.FieldBuilder.parseAnnotation;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.filament.mappingpoet.signature.AnnotationAwareDescriptors;
import net.fabricmc.filament.mappingpoet.signature.AnnotationAwareSignatures;
import net.fabricmc.filament.mappingpoet.signature.ClassSignature;
import net.fabricmc.filament.mappingpoet.signature.TypeAnnotationMapping;
import net.fabricmc.filament.mappingpoet.signature.TypeAnnotationStorage;

public class ClassBuilder {
	static final Handle OBJ_MTH_BOOTSTRAP = new Handle(
			Opcodes.H_INVOKESTATIC,
			"java/lang/runtime/ObjectMethods",
			"bootstrap",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
			false);

	private final MappingsStore mappings;
	private final ClassNode classNode;

	private final TypeSpec.Builder builder;
	private final List<ClassBuilder> innerClasses = new ArrayList<>();
	private final Environment environment;

	private final ClassSignature signature; // not really signature
	private final TypeAnnotationMapping typeAnnotations;
	private boolean annotationClass;
	private boolean enumClass;
	private boolean recordClass;
	private boolean instanceInner = false;
	// only nonnull if any class in the inner class chain creates a generic <T> decl
	// omits L and ;
	private String receiverSignature;

	public ClassBuilder(MappingsStore mappings, ClassNode classNode, Environment environment) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.environment = environment;
		this.typeAnnotations = setupAnnotations();
		this.signature = setupSignature();
		this.builder = setupBuilder();

		addInterfaces();
		addAnnotations();
		addJavaDoc();
	}

	public static ClassName parseInternalName(String internalName) {
		int classNameSeparator = -1;
		int index = 0;
		int nameStart = index;
		ClassName currentClassName = null;

		char ch;

		do {
			ch = index == internalName.length() ? ';' : internalName.charAt(index);

			if (ch == '$' || ch == ';') {
				// collect class name
				if (currentClassName == null) {
					String packageName = nameStart < classNameSeparator ? internalName.substring(nameStart, classNameSeparator).replace('/', '.') : "";
					String simpleName = internalName.substring(classNameSeparator + 1, index);
					currentClassName = ClassName.get(packageName, simpleName);
				} else {
					String simpleName = internalName.substring(classNameSeparator + 1, index);
					currentClassName = currentClassName.nestedClass(simpleName);
				}
			}

			if (ch == '/' || ch == '$') {
				// Start of simple name
				classNameSeparator = index;
			}

			index++;
		} while (ch != ';');

		if (currentClassName == null) {
			throw new IllegalArgumentException(String.format("Invalid internal name \"%s\"", internalName));
		}

		return currentClassName;
	}

	private TypeAnnotationMapping setupAnnotations() {
		return TypeAnnotationStorage.builder()
				.add(classNode.invisibleTypeAnnotations)
				.add(classNode.visibleTypeAnnotations)
				.build();
	}

	public void addMembers() {
		addMethods();
		addFields();
	}

	private ClassSignature setupSignature() {
		if (classNode.signature == null) {
			return AnnotationAwareDescriptors.parse(classNode.superName, classNode.interfaces, typeAnnotations, environment);
		} else {
			return AnnotationAwareSignatures.parseClassSignature(classNode.signature, typeAnnotations, environment);
		}
	}

	private TypeSpec.Builder setupBuilder() {
		TypeSpec.Builder builder;
		ClassName name = parseInternalName(classNode.name); // no type anno here

		if (Modifier.isInterface(classNode.access)) {
			if (classNode.interfaces.size() == 1 && classNode.interfaces.get(0).equals("java/lang/annotation/Annotation")) {
				builder = TypeSpec.annotationBuilder(name);
				this.annotationClass = true;
			} else {
				builder = TypeSpec.interfaceBuilder(name);
			}
		} else if (classNode.superName.equals("java/lang/Enum")) {
			enumClass = true;
			builder = TypeSpec.enumBuilder(name);
		} else if (classNode.superName.equals("java/lang/Record")) {
			recordClass = true;
			builder = TypeSpec.recordBuilder(name);
		} else {
			builder = TypeSpec.classBuilder(name)
					.superclass(signature.superclass());
		}

		if (!signature.generics().isEmpty()) {
			builder.addTypeVariables(signature.generics());
			StringBuilder sb = new StringBuilder();
			sb.append(classNode.name);
			sb.append("<");

			for (TypeVariableName each : signature.generics()) {
				sb.append("T").append(each.name).append(";");
			}

			sb.append(">");
			receiverSignature = sb.toString();
		}

		return builder
				.addModifiers(new ModifierBuilder(classNode.access)
						.checkUnseal(classNode, environment)
						.getModifiers(ModifierBuilder.getType(enumClass, recordClass)));
	}

	private void addInterfaces() {
		if (annotationClass) {
			return;
		}

		if (signature != null) {
			builder.addSuperinterfaces(signature.superinterfaces());
			return;
		}

		if (classNode.interfaces.isEmpty()) return;

		for (String iFace : classNode.interfaces) {
			builder.addSuperinterface(parseInternalName(iFace));
		}
	}

	private void addAnnotations() {
		// type anno already done through class sig
		addDirectAnnotations(classNode.invisibleAnnotations);
		addDirectAnnotations(classNode.visibleAnnotations);
	}

	private void addDirectAnnotations(List<AnnotationNode> regularAnnotations) {
		if (regularAnnotations == null) {
			return;
		}

		for (AnnotationNode annotation : regularAnnotations) {
			builder.addAnnotation(parseAnnotation(annotation));
		}
	}

	private void addMethods() {
		if (classNode.methods == null) return;

		methodsLoop: for (MethodNode method : classNode.methods) {
			if ((method.access & Opcodes.ACC_SYNTHETIC) != 0 || (method.access & Opcodes.ACC_MANDATED) != 0) {
				continue;
			}

			if (method.name.equals("<clinit>")) {
				continue;
			}

			int formalParamStartIndex = 0;

			if (enumClass) {
				// Skip enum sugar methods
				if (method.name.equals("values") && method.desc.equals("()[L" + classNode.name + ";")) {
					continue;
				}

				if (method.name.equals("valueOf") && method.desc.equals("(Ljava/lang/String;)L" + classNode.name + ";")) {
					continue;
				}

				if (method.name.equals("<init>")) {
					formalParamStartIndex = 2; // 0 String 1 int
				}
			}

			if (recordClass) {
				// skip record sugars
				if (method.name.equals("equals") && method.desc.equals("(Ljava/lang/Object;)Z")
						|| method.name.equals("toString") && method.desc.equals("()Ljava/lang/String;")
						|| method.name.equals("hashCode") && method.desc.equals("()I")) {
					for (AbstractInsnNode insn : method.instructions) {
						if (insn instanceof InvokeDynamicInsnNode indy
								&& indy.bsm.equals(OBJ_MTH_BOOTSTRAP)
								&& indy.name.equals(method.name)) {
							continue methodsLoop;
						}
					}
				}

				// todo test component getters
			}

			if (instanceInner) {
				if (method.name.equals("<init>")) {
					formalParamStartIndex = 1; // 0 this$0
				}
			}

			builder.addMethod(new MethodBuilder(mappings, classNode, method, environment, receiverSignature, formalParamStartIndex).build());
		}
	}

	private void addFields() {
		if (classNode.fields == null) return;

		for (FieldNode field : classNode.fields) {
			if (recordClass && !Modifier.isStatic(field.access)) {
				// proguard elevates record field access for direct record field gets
				if (!Modifier.isFinal(field.access) || Modifier.isProtected(field.access) || Modifier.isPublic(field.access)) {
					System.out.println("abnormal instance field " + field.name + " in record " + getClassName() + ", skipping");
				} else {
					var fieldBuilder = new FieldBuilder(mappings, classNode, field, environment);
					var paramBuilder = ParameterSpec.builder(fieldBuilder.calculateType(), field.name);
					fieldBuilder.addJavaDoc(paramBuilder);
					fieldBuilder.addDirectAnnotations(paramBuilder);
					builder.addRecordComponent(paramBuilder.build());
				}

				continue;
			}

			if ((field.access & Opcodes.ACC_SYNTHETIC) != 0 || (field.access & Opcodes.ACC_MANDATED) != 0) {
				continue; // hide synthetic stuff
			}

			if ((field.access & Opcodes.ACC_ENUM) == 0) {
				builder.addField(new FieldBuilder(mappings, classNode, field, environment).build());
			} else {
				TypeSpec.Builder enumBuilder = TypeSpec.anonymousClassBuilder("");
				// jd
				FieldBuilder.addFieldJavaDoc(enumBuilder, mappings, classNode, field);

				// annotations
				addDirectAnnotations(enumBuilder, field.invisibleAnnotations);
				addDirectAnnotations(enumBuilder, field.visibleAnnotations);
				List<AnnotationSpec> annotations = TypeAnnotationStorage.builder()
						.add(field.invisibleTypeAnnotations)
						.add(field.visibleTypeAnnotations)
						.build().getBank(TypeReference.newTypeReference(TypeReference.FIELD))
						.getCurrentAnnotations();

				if (!annotations.isEmpty()) {
					enumBuilder.addAnnotations(annotations); // no custom paths for annotations rip
				}

				builder.addEnumConstant(field.name, enumBuilder.build());
			}
		}
	}

	private void addDirectAnnotations(TypeSpec.Builder builder, List<AnnotationNode> regularAnnotations) {
		if (regularAnnotations == null) {
			return;
		}

		for (AnnotationNode annotation : regularAnnotations) {
			builder.addAnnotation(parseAnnotation(annotation));
		}
	}

	private void addJavaDoc() {
		mappings.addClassDoc(builder::addJavadoc, classNode.name);
	}

	public void addInnerClass(ClassBuilder classBuilder) {
		InnerClassNode innerClassNode = null;

		if (classNode.innerClasses != null) {
			for (InnerClassNode node : classNode.innerClasses) {
				if (node.name.equals(classBuilder.classNode.name)) {
					innerClassNode = node;
					break;
				}
			}
		}

		if (innerClassNode == null) {
			// fallback
			classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.PUBLIC);
			classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.STATIC);
		} else {
			if (innerClassNode.outerName == null) {
				// skip local classes and records, which have null outerName
				return;
			}

			if (classBuilder.classNode.outerMethod != null) {
				// local class per EnclosingMethod attribute
				return;
			}

			classBuilder.builder.modifiers.remove(javax.lang.model.element.Modifier.PUBLIC); // this modifier may come from class access
			classBuilder.builder.addModifiers(new ModifierBuilder(innerClassNode.access)
					.checkUnseal(classBuilder.classNode, environment)
					.getModifiers(ModifierBuilder.getType(classBuilder.enumClass, classBuilder.recordClass)));

			if (!Modifier.isStatic(innerClassNode.access)) {
				classBuilder.instanceInner = true;
			}

			// consider emit warning if this.instanceInner is true when classBuilder.instanceInner is false

			if (this.receiverSignature != null && classBuilder.instanceInner) {
				StringBuilder sb = new StringBuilder();
				sb.append(this.receiverSignature).append("."); // like O<TT;>. for O<T>
				sb.append(innerClassNode.innerName); // append simple name

				List<TypeVariableName> innerClassGenerics = classBuilder.signature.generics();

				if (!innerClassGenerics.isEmpty()) {
					sb.append("<");

					for (TypeVariableName each : innerClassGenerics) {
						sb.append("T").append(each.name).append(";");
					}

					sb.append(">");
				}

				classBuilder.receiverSignature = sb.toString();
			}
		}

		innerClasses.add(classBuilder);
	}

	public String getClassName() {
		return classNode.name;
	}

	public TypeSpec build() {
		for (ClassBuilder innerCb : innerClasses) {
			builder.addType(innerCb.build());
		}

		return builder.build();
	}
}
