package net.fabricmc.filament.enigma.annotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.TypeAnnotationKey;

public class AnnotationsEnigmaRemapper {
	private final ProjectView project;

	public AnnotationsEnigmaRemapper(ProjectView project) {
		this.project = project;
	}

	public ClassAnnotationData remap(String deobfClassName, ClassAnnotationData data) {
		Map<String, GenericAnnotationData> remappedFields = LinkedHashMap.newLinkedHashMap(data.fields().size());
		Map<String, MethodAnnotationData> remappedMethods = LinkedHashMap.newLinkedHashMap(data.methods().size());

		for (var fieldEntry : data.fields().entrySet()) {
			remappedFields.put(remapFieldKey(deobfClassName, fieldEntry.getKey()), remap(fieldEntry.getValue()));
		}

		for (var methodEntry : data.methods().entrySet()) {
			remappedMethods.put(remapMethodKey(deobfClassName, methodEntry.getKey()), remap(methodEntry.getValue()));
		}

		return new ClassAnnotationData(
				remapClasses(data.annotationsToRemove()),
				remapAnnotations(data.annotationsToAdd()),
				remapTypeAnnotationKeys(data.typeAnnotationsToRemove()),
				remapTypeAnnotations(data.typeAnnotationsToAdd()),
				remappedFields,
				remappedMethods

		);
	}

	private GenericAnnotationData remap(GenericAnnotationData data) {
		return new GenericAnnotationData(
				remapClasses(data.annotationsToRemove()),
				remapAnnotations(data.annotationsToAdd()),
				remapTypeAnnotationKeys(data.typeAnnotationsToRemove()),
				remapTypeAnnotations(data.typeAnnotationsToAdd())
		);
	}

	private MethodAnnotationData remap(MethodAnnotationData data) {
		Map<Integer, GenericAnnotationData> remappedParameters = LinkedHashMap.newLinkedHashMap(data.parameters().size());

		for (var paramEntry : data.parameters().entrySet()) {
			remappedParameters.put(paramEntry.getKey(), remap(paramEntry.getValue()));
		}

		return new MethodAnnotationData(
				remapClasses(data.annotationsToRemove()),
				remapAnnotations(data.annotationsToAdd()),
				remapTypeAnnotationKeys(data.typeAnnotationsToRemove()),
				remapTypeAnnotations(data.typeAnnotationsToAdd()),
				remappedParameters
		);
	}

	private String remapFieldKey(String deobfClassName, String fieldKey) {
		String[] nameAndDesc = fieldKey.split(":", 2);

		if (nameAndDesc.length != 2) {
			return fieldKey;
		}

		return project.obfuscate(FieldEntryView.create(deobfClassName, nameAndDesc[0], nameAndDesc[1])).getName() + ":" + remapDesc(nameAndDesc[1]);
	}

	private String remapMethodKey(String deobfClassName, String methodKey) {
		int parenIndex = methodKey.indexOf('(');

		if (parenIndex == -1) {
			return methodKey;
		}

		String name = methodKey.substring(0, parenIndex);
		String desc = methodKey.substring(parenIndex);

		return project.obfuscate(MethodEntryView.create(deobfClassName, name, desc)).getName() + remapMethodDesc(desc);
	}

	private Set<String> remapClasses(Set<String> classNames) {
		return classNames
				.stream()
				.map(this::remapClass)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private List<AnnotationNode> remapAnnotations(List<AnnotationNode> annotations) {
		return annotations
				.stream()
				.map(this::remap)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private Set<TypeAnnotationKey> remapTypeAnnotationKeys(Set<TypeAnnotationKey> keys) {
		return keys
				.stream()
				.map(key -> new TypeAnnotationKey(key.typeRef(), key.typePath(), remapClass(key.name())))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private List<TypeAnnotationNode> remapTypeAnnotations(List<TypeAnnotationNode> annotations) {
		return annotations
				.stream()
				.map(this::remap)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private AnnotationNode remap(AnnotationNode annotation) {
		return remap(annotation, new AnnotationNode(remapDesc(annotation.desc)));
	}

	private TypeAnnotationNode remap(TypeAnnotationNode annotation) {
		return remap(annotation, new TypeAnnotationNode(annotation.typeRef, annotation.typePath, remapDesc(annotation.desc)));
	}

	private <T extends AnnotationNode> T remap(T annotation, T remapped) {
		ClassNode annotationClass = project.getBytecode(Type.getType(remapped.desc).getInternalName());

		if (annotation.values != null) {
			remapped.values = new ArrayList<>(annotation.values);

			for (int i = 0; i < remapped.values.size(); i += 2) {
				// remap attribute name
				if (annotationClass != null) {
					for (MethodNode method : annotationClass.methods) {
						String deobfMethod = project.deobfuscate(MethodEntryView.create(annotationClass.name, method.name, method.desc)).getName();

						if (deobfMethod.equals(remapped.values.get(i))) {
							remapped.values.set(i, method.name);
						}
					}
				}

				// remap attribute value
				remapped.values.set(i + 1, remapValue(remapped.values.get(i + 1)));
			}
		}

		return remapped;
	}

	private Object remapValue(Object value) {
		return switch (value) {
		case String[] enumValue -> new String[] {
				remapDesc(enumValue[0]),
				project.obfuscate(FieldEntryView.create(Type.getType(enumValue[0]).getInternalName(), enumValue[1], enumValue[0])).getName()
		};
		case Type clazz -> remap(clazz);
		case AnnotationNode annotation -> remap(annotation);
		case List<?> list -> list.stream().map(this::remapValue).collect(Collectors.toCollection(ArrayList::new));
		default -> value;
		};
	}

	private String remapClass(String className) {
		return project.obfuscate(ClassEntryView.create(className)).getFullName();
	}

	private String remapDesc(String desc) {
		return remap(Type.getType(desc)).getDescriptor();
	}

	private String remapMethodDesc(String methodDesc) {
		StringBuilder sb = new StringBuilder("(");
		Type methodType = Type.getMethodType(methodDesc);

		for (Type argumentType : methodType.getArgumentTypes()) {
			sb.append(remap(argumentType).getDescriptor());
		}

		return sb.append(")").append(remap(methodType.getReturnType()).getDescriptor()).toString();
	}

	private Type remap(Type type) {
		if (type.getSort() == Type.ARRAY) {
			return Type.getType("[".repeat(type.getDimensions()) + remap(type.getElementType()).getDescriptor());
		} else if (type.getSort() == Type.OBJECT) {
			return Type.getObjectType(remapClass(type.getInternalName()));
		} else {
			return type;
		}
	}
}
