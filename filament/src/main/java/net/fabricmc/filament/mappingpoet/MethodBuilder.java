package net.fabricmc.mappingpoet;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MethodBuilder {
	private final MappingsStore mappings;
	private final ClassNode classNode;
	private final MethodNode methodNode;
	private final MethodSpec.Builder builder;

	public MethodBuilder(MappingsStore mappings, ClassNode classNode, MethodNode methodNode) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.methodNode = methodNode;
		this.builder = createBuilder();
		addJavaDoc();
		setReturnType();
		addParameters();
	}

	private MethodSpec.Builder createBuilder() {
		return MethodSpec.methodBuilder(methodNode.name)
				.addModifiers(new ModifierBuilder(methodNode.access).getModifiers(ModifierBuilder.Type.METHOD));
	}

	private void setReturnType() {
		//Skip constructors
		if (methodNode.name.equals("<init>")) {
			return;
		}
		String returnDesc = methodNode.desc.substring(methodNode.desc.lastIndexOf(")") + 1);

		TypeName typeName = FieldBuilder.getFieldType(returnDesc);
		builder.returns(typeName);
		if (typeName != TypeName.VOID && !java.lang.reflect.Modifier.isAbstract(methodNode.access)) {
			builder.addStatement("throw new RuntimeException();");
		}
	}

	private void addParameters() {
		List<ParamType> paramTypes = new ArrayList<>();
		getParams(methodNode.desc, paramTypes);
		for (ParamType paramType : paramTypes) {
			builder.addParameter(paramType.type, paramType.name, paramType.modifiers);
		}
	}

	//TODO this prob needs a rewrite
	private void getParams(String desc, List<ParamType> paramTypes) {
		if (desc.isEmpty() || desc.charAt(0) == ')') {
			return;
		}
		if (desc.charAt(0) == '(') {
			getParams(desc.substring(1), paramTypes);
			return;
		}
		TypeName type = TypeName.VOID;

		int width = 1;
		char cha = desc.charAt(0);

		switch (cha) {
			case 'B':
				type = TypeName.BYTE;
				break;
			case 'C':
				type = TypeName.CHAR;
				break;
			case 'S':
				type = TypeName.SHORT;
				break;
			case 'Z':
				type = TypeName.BOOLEAN;
				break;
			case 'I':
				type = TypeName.INT;
				break;
			case 'J':
				type = TypeName.LONG;
				break;
			case 'F':
				type = TypeName.FLOAT;
				break;
			case 'D':
				type = TypeName.DOUBLE;
				break;
			case 'L' :
				desc = desc.substring(1);
				String clazz = desc.substring(0, desc.indexOf(";"));
				width = clazz.length() + 1;
				type = ClassBuilder.getClassName(clazz);
		}

		paramTypes.add(new ParamType("fixme", type, paramTypes.size()));
		getParams(desc.substring(width), paramTypes);
	}

	private class ParamType {
		private final String name;
		private final TypeName type;
		private final Modifier[] modifiers;

		public ParamType(String name, TypeName type, int index) {
			this.name = getValidName(type) + "_" + index;
			this.type = type;
			this.modifiers = new ModifierBuilder(0)
					.getModifiers(ModifierBuilder.Type.PARAM);
		}

		private String getValidName(TypeName type) {
			String str = type.toString();
			if (str.contains(".")) {
				str = str.substring(str.lastIndexOf(".") + 1);
			}
			str = Character.toLowerCase(str.charAt(0)) + str.substring(1);

			if (str.equals("boolean")) {
				str = "bool";
			}
			return str;
		}
	}

	private void addJavaDoc() {
		String javaDoc = mappings.getMethodDoc(new EntryTriple(classNode.name, methodNode.name, methodNode.desc));
		if (javaDoc != null) {
			builder.addJavadoc(javaDoc);
		}
	}

	private TypeName getFieldType(String desc) {
		switch (desc) {
			case "B":
				return TypeName.BYTE;
			case "C":
				return TypeName.CHAR;
			case "S":
				return TypeName.SHORT;
			case "Z":
				return TypeName.BOOLEAN;
			case "I":
				return TypeName.INT;
			case "J":
				return TypeName.LONG;
			case "F":
				return TypeName.FLOAT;
			case "D":
				return TypeName.DOUBLE;
		}
		if (desc.startsWith("[")) {
			return ArrayTypeName.of(getFieldType(desc.substring(1)));
		}
		if (desc.startsWith("L")) {
			return ClassBuilder.getClassName(desc.substring(1).substring(0, desc.length() -2));
		}
		throw new UnsupportedOperationException("Unknown field type" + desc);
	}

	public MethodSpec build() {
		return builder.build();
	}
}
