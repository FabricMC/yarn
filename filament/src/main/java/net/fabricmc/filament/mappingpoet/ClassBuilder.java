package net.fabricmc.mappingpoet;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;

public class ClassBuilder {

	private final MappingsStore mappings;
	private final ClassNode classNode;

	private final TypeSpec.Builder builder;

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
		return TypeSpec.classBuilder(getClassName(classNode.name))
				.addModifiers(new ModifierBuilder(classNode.access).getModifiers(ModifierBuilder.Type.CLASS))
				.superclass(getClassName(classNode.superName));
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

	public TypeSpec buildTypeSpec() {
		return builder.build();
	}

	public static ClassName getClassName(String input) {
		int lastDelim = input.lastIndexOf("/");
		return ClassName.get(input.substring(0, lastDelim).replaceAll("/", ".").replaceAll("\\$", "."), input.substring(lastDelim + 1));
	}
}
