package net.fabricmc.mappingpoet;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassBuilder {

	private final MappingsStore mappings;
	private final ClassNode classNode;

	private final TypeSpec.Builder builder;
	private final List<ClassBuilder> innerClasses = new ArrayList<>();
	private final Function<String, Collection<String>> superGetter;

	private Signatures.ClassSignature signature;
	private boolean enumClass;

	public ClassBuilder(MappingsStore mappings, ClassNode classNode, Function<String, Collection<String>> superGetter) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.superGetter = superGetter;
		this.builder = setupBuilder();
		addInterfaces();
		addDirectAnnotations();
		addMethods();
		addFields();
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

	private TypeSpec.Builder setupBuilder() {
		if (classNode.signature != null) {
			signature = Signatures.parseClassSignature(classNode.signature);
		}
		TypeSpec.Builder builder;
		if (Modifier.isInterface(classNode.access)) {
			builder = TypeSpec.interfaceBuilder(parseInternalName(classNode.name));
		} else if (classNode.superName.equals("java/lang/Enum")) {
			enumClass = true;
			builder = TypeSpec.enumBuilder(parseInternalName(classNode.name));
		} else {
			builder = TypeSpec.classBuilder(parseInternalName(classNode.name))
					.superclass(signature == null ? parseInternalName(classNode.superName) : signature.superclass);
		}

		if (signature != null && signature.generics != null) {
			builder.addTypeVariables(signature.generics);
		}

		return builder
				.addModifiers(new ModifierBuilder(classNode.access).getModifiers(enumClass ? ModifierBuilder.Type.ENUM : ModifierBuilder.Type.CLASS));
	}

	private void addInterfaces() {
		if (signature != null) {
			builder.addSuperinterfaces(signature.superinterfaces);
			return;
		}
		if (classNode.interfaces == null) return;

		for (String iFace : classNode.interfaces) {
			builder.addSuperinterface(parseInternalName(iFace));
		}
	}

	private void addDirectAnnotations() {
		addDirectAnnotations(classNode.invisibleAnnotations);
		addDirectAnnotations(classNode.visibleAnnotations);
	}

	private void addDirectAnnotations(List<AnnotationNode> regularAnnotations) {
		if (regularAnnotations == null) {
			return;
		}
		for (AnnotationNode annotation : regularAnnotations) {
			builder.addAnnotation(FieldBuilder.parseAnnotation(annotation));
		}
	}

	private void addMethods() {
		if (classNode.methods == null) return;
		for (MethodNode method : classNode.methods) {
			if ((method.access & Opcodes.ACC_SYNTHETIC) != 0 || (method.access & Opcodes.ACC_MANDATED) != 0) {
				continue;
			}
			if (method.name.equals("<clinit>")) {
				continue;
			}
			if (enumClass) {
				// Skip enum sugar methods
				if (method.name.equals("values") && method.desc.equals("()[L" + classNode.name + ";")) {
					continue;
				}
				if (method.name.equals("valueOf") && method.desc.equals("(Ljava/lang/String;)L" + classNode.name + ";")) {
					continue;
				}
			}
			builder.addMethod(new MethodBuilder(mappings, classNode, method, superGetter).build());
		}
	}

	private void addFields() {
		if (classNode.fields == null) return;
		for (FieldNode field : classNode.fields) {
			if ((field.access & Opcodes.ACC_SYNTHETIC) != 0 || (field.access & Opcodes.ACC_MANDATED) != 0) {
				continue; // hide synthetic stuff
			}
			if ((field.access & Opcodes.ACC_ENUM) == 0) {
				builder.addField(new FieldBuilder(mappings, classNode, field).build());
			} else {
				TypeSpec.Builder enumBuilder = TypeSpec.anonymousClassBuilder("");
				FieldBuilder.addFieldJavaDoc(enumBuilder, mappings, classNode, field);
				builder.addEnumConstant(field.name, enumBuilder.build());
			}
		}
	}

	private void addJavaDoc() {
		String javadoc = mappings.getClassDoc(classNode.name);
		if (javadoc != null) {
			builder.addJavadoc(javadoc);
		}
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
			classBuilder.builder.addModifiers(new ModifierBuilder(innerClassNode.access).getModifiers(classBuilder.enumClass ? ModifierBuilder.Type.ENUM : ModifierBuilder.Type.CLASS));
		}
		innerClasses.add(classBuilder);
	}

	public String getClassName() {
		return classNode.name;
	}

	public TypeSpec build() {
		innerClasses.stream()
				.map(ClassBuilder::build)
				.forEach(builder::addType);
		return builder.build();
	}
}
