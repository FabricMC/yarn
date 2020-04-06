package net.fabricmc.mappingpoet;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassBuilder {

	private final MappingsStore mappings;
	private final ClassNode classNode;

	private final TypeSpec.Builder builder;
	private final List<ClassBuilder> innerClasses = new ArrayList<>();

	public ClassBuilder(MappingsStore mappings, ClassNode classNode) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.builder = setupBuilder();
		addInterfaces();
		addMethods();
		addFields();
		addJavaDoc();
	}

	private TypeSpec.Builder setupBuilder() {
		TypeSpec.Builder builder;
		if (Modifier.isInterface(classNode.access)) {
			builder = TypeSpec.interfaceBuilder(getClassName(classNode.name));
		} else if (classNode.superName.equals("java.lang.Enum")) {
			builder = TypeSpec.enumBuilder(getClassName(classNode.name));
		} else {
			builder = TypeSpec.classBuilder(getClassName(classNode.name))
					.superclass(getClassName(classNode.superName));
		}

		return builder
				.addModifiers(new ModifierBuilder(classNode.access).getModifiers(ModifierBuilder.Type.CLASS));
	}

	private void addInterfaces() {
		if (classNode.interfaces == null) return;

		for (String iFace :classNode.interfaces){
			builder.addSuperinterface(getClassName(iFace));
		}
	}
	
	private void addMethods() {
		if (classNode.methods == null) return;
		for (MethodNode method : classNode.methods) {
			if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) {
				continue;
			}
			if (method.name.equals("<clinit>")) {
				continue;
			}
			builder.addMethod(new MethodBuilder(mappings, classNode, method).build());
		}
	}

	private void addFields() {
		if (classNode.fields == null) return;
		for (FieldNode field : classNode.fields) {
			builder.addField(new FieldBuilder(mappings, classNode, field).build());
		}
	}
	
	private void addJavaDoc() {
		String javadoc = mappings.getClassDoc(classNode.name);
		if (javadoc != null) {
			builder.addJavadoc(javadoc);
		}
	}

	public void addInnerClass(ClassBuilder classBuilder) {
		classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.PUBLIC);
		classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.STATIC);
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

	public static ClassName getClassName(String input) {
		int lastDelim = input.lastIndexOf("/");
		String packageName = input.substring(0, lastDelim).replaceAll("/", ".");
		String className = input.substring(lastDelim + 1).replaceAll("/", ".");

		List<String> classSplit = new ArrayList<>(Arrays.asList(className.split("\\$")));
		String parentClass = classSplit.get(0);
		classSplit.remove(0);

		return ClassName.get(packageName, parentClass, classSplit.toArray(new String[]{}));
	}
}
