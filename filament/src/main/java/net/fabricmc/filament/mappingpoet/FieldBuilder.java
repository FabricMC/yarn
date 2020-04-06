package net.fabricmc.mappingpoet;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class FieldBuilder {
	private final MappingsStore mappings;
	private final ClassNode classNode;
	private final FieldNode fieldNode;
	private final FieldSpec.Builder builder;

	public FieldBuilder(MappingsStore mappings, ClassNode classNode, FieldNode fieldNode) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.fieldNode = fieldNode;
		this.builder = createBuilder();
		addJavaDoc();
	}

	private FieldSpec.Builder createBuilder() {
		return FieldSpec.builder(getFieldType(fieldNode.desc), fieldNode.name)
				.addModifiers(new ModifierBuilder(fieldNode.access).getModifiers(ModifierBuilder.Type.FIELD));
	}

	private void addJavaDoc() {
		String javaDoc = mappings.getFieldDoc(new EntryTriple(classNode.name, fieldNode.name, fieldNode.desc));
		if (javaDoc != null) {
			builder.addJavadoc(javaDoc);
		}
	}

	public static TypeName getFieldType(String desc) {
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
			case "V":
				return TypeName.VOID;
		}
		if (desc.startsWith("[")) {
			return ArrayTypeName.of(getFieldType(desc.substring(1)));
		}
		if (desc.startsWith("L")) {
			return ClassBuilder.getClassName(desc.substring(1).substring(0, desc.length() -2));
		}
		throw new UnsupportedOperationException("Unknown field type" + desc);
	}

	public FieldSpec build() {
		return builder.build();
	}
}
