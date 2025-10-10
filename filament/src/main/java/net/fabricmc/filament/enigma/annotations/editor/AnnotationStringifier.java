package net.fabricmc.filament.enigma.annotations.editor;

import java.util.List;

import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.filament.enigma.annotations.AnnotationUtil;

public class AnnotationStringifier {
	private ProjectView project;
	private boolean deobfuscate;
	private boolean shortenClassReferences;

	public String stringify(AnnotationNode annotation) {
		StringBuilder sb = new StringBuilder();
		annotationToString(sb, annotation);
		return sb.toString();
	}

	public AnnotationStringifier deobfuscateWith(ProjectView project) {
		this.project = project;
		this.deobfuscate = true;
		return this;
	}

	public AnnotationStringifier shortenClassReferences() {
		this.shortenClassReferences = true;
		return this;
	}

	private void annotationToString(StringBuilder sb, AnnotationNode annotation) {
		String obfName = annotation.desc.substring(1, annotation.desc.length() - 1);
		String deobfName = deobfuscateClass(obfName);
		sb.append('@').append(stringifyClassReference(deobfName));

		if (annotation.values == null || annotation.values.isEmpty()) {
			return;
		}

		sb.append('(');

		if (annotation.values.size() == 2 && "value".equals(annotation.values.getFirst())) {
			annotationValueToString(sb, annotation.values.getLast());
		} else {
			for (int i = 0; i < annotation.values.size(); i += 2) {
				if (i != 0) {
					sb.append(", ");
				}

				sb.append(deobfuscateAnnotationValue(obfName, (String) annotation.values.get(i)));
				sb.append(" = ");
				annotationValueToString(sb, annotation.values.get(i + 1));
			}
		}

		sb.append(')');
	}

	private void annotationValueToString(StringBuilder sb, Object annotationValue) {
		switch (annotationValue) {
		case Character c -> quoteString(sb, c.toString(), '\'');
		case String s -> quoteString(sb, s, '"');
		case Float f -> sb.append(f).append('F');
		case Long l -> sb.append(l).append('L');
		case org.objectweb.asm.Type t -> {
			int arrayDimensions = 0;

			if (t.getSort() == org.objectweb.asm.Type.ARRAY) {
				arrayDimensions = t.getDimensions();
				t = t.getElementType();
			}

			if (t.getSort() == org.objectweb.asm.Type.OBJECT) {
				sb.append(stringifyClassReference(deobfuscateClass(t.getInternalName())));
			} else {
				sb.append(t.getClassName());
			}

			sb.append("[]".repeat(arrayDimensions));
			sb.append(".class");
		}
		case String[] enumValue -> {
			String obfDesc = enumValue[0];
			String obfOwner = obfDesc.substring(1, obfDesc.length() - 1);
			String deobfOwner = deobfuscateClass(obfOwner);
			String deobfName = deobfuscateField(obfOwner, enumValue[1], obfDesc);
			sb.append(stringifyClassReference(deobfOwner)).append('.').append(deobfName);
		}
		case AnnotationNode ann -> annotationToString(sb, ann);
		case List<?> list -> {
			if (list.size() == 1) {
				annotationValueToString(sb, list.getFirst());
			} else {
				sb.append('{');

				for (int i = 0; i < list.size(); i++) {
					if (i != 0) {
						sb.append(", ");
					}

					annotationValueToString(sb, list.get(i));
				}

				sb.append('}');
			}
		}
		default -> sb.append(annotationValue);
		}
	}

	private String deobfuscateClass(String className) {
		if (!deobfuscate) {
			return className;
		}

		return project.deobfuscate(ClassEntryView.create(className)).getFullName();
	}

	private String deobfuscateField(String className, String fieldName, String fieldDesc) {
		if (!deobfuscate) {
			return fieldName;
		}

		return project.deobfuscate(FieldEntryView.create(className, fieldName, fieldDesc)).getName();
	}

	private String deobfuscateAnnotationValue(String annotationName, String valueName) {
		if (!deobfuscate) {
			return valueName;
		}

		ClassNode bytecode = project.getBytecode(annotationName);

		if (bytecode == null || bytecode.methods == null) {
			return valueName;
		}

		for (MethodNode method : bytecode.methods) {
			if ((method.access & Opcodes.ACC_ABSTRACT) != 0 && method.name.equals(valueName)) {
				return project.deobfuscate(MethodEntryView.create(annotationName, method.name, method.desc)).getName();
			}
		}

		return valueName;
	}

	private String stringifyClassReference(String internalName) {
		if (!shortenClassReferences) {
			return internalName.replace('/', '.').replace('$', '.');
		}

		return AnnotationUtil.getSimpleName(internalName);
	}

	private static void quoteString(StringBuilder sb, String str, char quoteChar) {
		sb.append(quoteChar);

		str.chars().forEach(ch -> {
			switch (ch) {
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			case '\f' -> sb.append("\\f");
			case '\b' -> sb.append("\\b");
			default -> {
				if (ch < ' ') {
					sb.append('\\').append(Integer.toOctalString(ch));
				} else if (ch == quoteChar) {
					sb.append('\\').append(quoteChar);
				} else {
					sb.appendCodePoint(ch);
				}
			}
			}
		});

		sb.append(quoteChar);
	}
}
