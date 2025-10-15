package net.fabricmc.filament.enigma.annotations.editor;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.LocalVariableEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.filament.enigma.annotations.AnnotationUtil;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.BaseAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;

public class DeclarationGenerator {
	private final ProjectView project;
	private final AnnotationsEditor editor;

	public DeclarationGenerator(ProjectView project, AnnotationsEditor editor) {
		this.project = project;
		this.editor = editor;
	}

	public TextWithButtons generate() {
		return switch (editor.getDeclaration()) {
		case ClassNode classNode -> generate(classNode);
		case FieldNode fieldNode -> generate(fieldNode);
		case MethodNode methodNode -> generate(methodNode);
		default -> throw new IllegalStateException("Unsupported declaration type: " + editor.getDeclaration().getClass().getName());
		};
	}

	private TextWithButtons generate(ClassNode declaration) {
		TextWithButtons result = new TextWithButtons();

		ClassDeclType declType = ClassDeclType.infer(declaration);

		Set<ElementType> targets = EnumSet.of(ElementType.TYPE);

		if (declType == ClassDeclType.ANNOTATION) {
			targets.add(ElementType.ANNOTATION_TYPE);
		}

		addTopLevelAnnotations(result, targets, declaration.invisibleAnnotations, declaration.visibleAnnotations);

		result.append(declType.getKeyword());
		result.append(" ");
		result.append(AnnotationUtil.getSimpleName(project.deobfuscate(ClassEntryView.create(declaration.name)).getFullName()));

		appendTypeParameters(result, declaration.signature, TypeReference.CLASS_TYPE_PARAMETER, TypeReference.CLASS_TYPE_PARAMETER_BOUND);

		if (declType == ClassDeclType.CLASS && declaration.superName != null) {
			result.append(" extends ");

			if (declaration.signature != null) {
				new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
					@Override
					public SignatureVisitor visitSuperclass() {
						return new TypeRefAppender(result, TypeReference.newSuperTypeReference(-1).getValue());
					}
				});
			} else {
				TypeRefAppender appender = new TypeRefAppender(result, TypeReference.newSuperTypeReference(-1).getValue());
				appender.visitClassType(declaration.superName);
				appender.visitEnd();
			}
		}

		boolean hasInterfaces = false;

		if (declaration.interfaces != null) {
			for (String itf : declaration.interfaces) {
				if (!itf.equals("java/lang/annotation/Annotation")) {
					hasInterfaces = true;
					break;
				}
			}
		}

		if (hasInterfaces) {
			if (declType.isInterface()) {
				result.append(" extends ");
			} else {
				result.append(" implements ");
			}

			if (declaration.signature != null) {
				new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
					boolean addedInterface = false;
					int interfaceIndex = 0;

					@Override
					public SignatureVisitor visitInterface() {
						int interfaceIndex = this.interfaceIndex++;

						if (declaration.interfaces.get(interfaceIndex).equals("java/lang/annotation/Annotation")) {
							return this;
						}

						if (addedInterface) {
							result.append(", ");
						}

						addedInterface = true;
						return new TypeRefAppender(result, TypeReference.newSuperTypeReference(interfaceIndex).getValue());
					}
				});
			} else {
				boolean addedInterface = false;

				for (int i = 0; i < declaration.interfaces.size(); i++) {
					String itf = declaration.interfaces.get(i);

					if (itf.equals("java/lang/annotation/Annotation")) {
						continue;
					}

					if (addedInterface) {
						result.append(", ");
					}

					addedInterface = true;
					TypeRefAppender appender = new TypeRefAppender(result, TypeReference.newSuperTypeReference(i).getValue());
					appender.visitClassType(itf);
					appender.visitEnd();
				}
			}
		}

		result.append(";");

		return result;
	}

	private TextWithButtons generate(FieldNode declaration) {
		TextWithButtons result = new TextWithButtons();

		String effectiveSignature = Objects.requireNonNullElse(declaration.signature, declaration.desc);
		int signatureArrayDims = 0;

		while (signatureArrayDims < effectiveSignature.length() && effectiveSignature.charAt(signatureArrayDims) == '[') {
			signatureArrayDims++;
		}

		Set<ElementType> targets = EnumSet.of(ElementType.FIELD);

		if ((editor.getContainingClass().access & Opcodes.ACC_RECORD) != 0) {
			targets.add(ElementType.RECORD_COMPONENT);
			targets.add(ElementType.METHOD);
			targets.add(ElementType.PARAMETER);
		}

		addTopLevelBiPurposeAnnotations(
				result,
				"\n",
				TypeReference.newTypeReference(TypeReference.FIELD).getValue(),
				signatureArrayDims == 0 ? null : TypePath.fromString("[".repeat(signatureArrayDims)),
				targets,
				declaration.invisibleAnnotations,
				declaration.visibleAnnotations,
				declaration.invisibleTypeAnnotations,
				declaration.visibleTypeAnnotations,
				(create, isTypeAnnotation) -> editor.getData()
		);

		new SignatureReader(effectiveSignature).acceptType(new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.FIELD).getValue(), false));
		result.append(" ");
		result.append(project.deobfuscate(editor.getEditingEntry()).getName());
		result.append(";");

		return result;
	}

	private TextWithButtons generate(MethodNode declaration) {
		TextWithButtons result = new TextWithButtons();

		boolean isConstructor = "<init>".equals(declaration.name);
		ClassEntryView obfOwnerEntry = ((MethodEntryView) editor.getEditingEntry()).getParent();
		String deobfOwnerName = project.deobfuscate(obfOwnerEntry).getFullName();

		int returnTypeSignatureArrayDims;

		if (declaration.signature != null) {
			var visitor = new SignatureVisitor(Opcodes.ASM9) {
				int arrayDims = 0;
				boolean inReturnTypeArray = false;

				@Override
				public SignatureVisitor visitReturnType() {
					inReturnTypeArray = true;
					return this;
				}

				@Override
				public SignatureVisitor visitArrayType() {
					if (inReturnTypeArray) {
						arrayDims++;
					}

					return this;
				}

				@Override
				public void visitBaseType(char descriptor) {
					inReturnTypeArray = false;
				}

				@Override
				public void visitTypeVariable(String name) {
					inReturnTypeArray = false;
				}

				@Override
				public void visitClassType(String name) {
					inReturnTypeArray = false;
				}
			};
			new SignatureReader(declaration.signature).accept(visitor);
			returnTypeSignatureArrayDims = visitor.arrayDims;
		} else {
			Type returnType = Type.getReturnType(declaration.desc);
			returnTypeSignatureArrayDims = returnType.getSort() == Type.ARRAY ? returnType.getDimensions() : 0;
		}

		if (Type.getReturnType(declaration.desc) == Type.VOID_TYPE) {
			Set<ElementType> targets = EnumSet.of(ElementType.METHOD);

			if (isConstructor) {
				targets.add(ElementType.CONSTRUCTOR);
			}

			addTopLevelAnnotations(result, targets, declaration.invisibleAnnotations, declaration.visibleAnnotations);
		} else {
			addTopLevelBiPurposeAnnotations(
					result,
					"\n",
					TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(),
					returnTypeSignatureArrayDims == 0 ? null : TypePath.fromString("[".repeat(returnTypeSignatureArrayDims)),
					EnumSet.of(ElementType.METHOD),
					declaration.invisibleAnnotations,
					declaration.visibleAnnotations,
					declaration.invisibleTypeAnnotations,
					declaration.visibleTypeAnnotations,
					(create, isTypeAnnotation) -> editor.getData()
			);
		}

		if (appendTypeParameters(result, declaration.signature, TypeReference.METHOD_TYPE_PARAMETER, TypeReference.METHOD_TYPE_PARAMETER_BOUND)) {
			result.append(" ");
		}

		if (isConstructor) {
			result.append(AnnotationUtil.getSimpleName(deobfOwnerName));
		} else {
			if (declaration.signature != null) {
				new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
					@Override
					public SignatureVisitor visitReturnType() {
						return new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(), false);
					}
				});
			} else {
				new SignatureReader(Type.getReturnType(declaration.desc).getDescriptor())
						.acceptType(new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(), false));
			}

			result.append(" ");
			result.append(project.deobfuscate(editor.getEditingEntry()).getName());
		}

		result.append("(");

		boolean hasThisParam = (declaration.access & Opcodes.ACC_STATIC) == 0 && !isConstructor;

		if (hasThisParam) {
			result.append("\n\t");
			TypeRefAppender appender = new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.METHOD_RECEIVER).getValue());
			appender.visitClassType(obfOwnerEntry.getFullName());
			appender.visitEnd();

			result.append(" this");
		}

		class ParameterInfo {
			final String variableName;
			TextWithButtons typeWithButtons;
			int signatureArrayDims;

			ParameterInfo(String variableName) {
				this.variableName = variableName;
			}
		}

		Set<String> usedVariableNames = new HashSet<>();
		List<ParameterInfo> parameterInfos = new ArrayList<>();
		int lvIndex = (declaration.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
		Type[] paramTypes = Type.getArgumentTypes(declaration.desc);
		List<ParameterNode> params = Objects.requireNonNullElse(declaration.parameters, List.of());

		for (int i = 0; i < paramTypes.length; i++) {
			if (i >= params.size() || (params.get(i).access & Opcodes.ACC_SYNTHETIC) == 0) {
				String variableName = project.deobfuscate(
						LocalVariableEntryView.create((MethodEntryView) editor.getEditingEntry(), lvIndex, generateVariableName(paramTypes[i]), true)
				).getName();

				if (usedVariableNames.contains(variableName)) {
					String baseVariableName = variableName;
					int j = 1;

					do {
						variableName = baseVariableName + j++;
					} while (usedVariableNames.contains(variableName));
				}

				usedVariableNames.add(variableName);
				parameterInfos.add(new ParameterInfo(variableName));
			}

			lvIndex += paramTypes[i].getSize();
		}

		if (declaration.signature != null) {
			new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
				int paramIndex = -1;

				@Override
				public SignatureVisitor visitParameterType() {
					paramIndex++;
					TextWithButtons paramTypeWithButtons = new TextWithButtons();

					if (paramIndex < parameterInfos.size()) {
						parameterInfos.get(paramIndex).typeWithButtons = paramTypeWithButtons;
					}

					return new TypeRefAppender(paramTypeWithButtons, TypeReference.newFormalParameterReference(paramIndex).getValue(), false);
				}
			});

			new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
				int paramIndex = -1;
				@Nullable
				ParameterInfo currentParameterInfo;

				@Override
				public SignatureVisitor visitParameterType() {
					paramIndex++;

					if (paramIndex < parameterInfos.size()) {
						currentParameterInfo = parameterInfos.get(paramIndex);
					}

					return this;
				}

				@Override
				public SignatureVisitor visitArrayType() {
					if (currentParameterInfo != null) {
						currentParameterInfo.signatureArrayDims++;
					}

					return this;
				}

				@Override
				public void visitBaseType(char descriptor) {
					currentParameterInfo = null;
				}

				@Override
				public void visitClassType(String name) {
					currentParameterInfo = null;
				}

				@Override
				public void visitTypeVariable(String name) {
					currentParameterInfo = null;
				}
			});
		} else {
			int nonSyntheticParamIndex = -1;

			for (int i = 0; i < paramTypes.length; i++) {
				if (i < params.size() && (params.get(i).access & Opcodes.ACC_SYNTHETIC) != 0) {
					continue;
				}

				nonSyntheticParamIndex++;

				if (nonSyntheticParamIndex >= parameterInfos.size()) {
					continue;
				}

				TextWithButtons paramTypeWithButtons = new TextWithButtons();
				parameterInfos.get(nonSyntheticParamIndex).typeWithButtons = paramTypeWithButtons;
				new SignatureReader(paramTypes[i].getDescriptor())
						.acceptType(new TypeRefAppender(paramTypeWithButtons, TypeReference.newFormalParameterReference(nonSyntheticParamIndex).getValue(), false));

				parameterInfos.get(nonSyntheticParamIndex).signatureArrayDims = paramTypes[i].getSort() == Type.ARRAY ? paramTypes[i].getDimensions() : 0;
			}
		}

		for (int i = 0; i < parameterInfos.size(); i++) {
			int paramIndex = i;
			ParameterInfo paramInfo = parameterInfos.get(i);

			if (paramInfo.typeWithButtons == null) {
				continue;
			}

			if (i != 0 || hasThisParam) {
				result.append(",");
			}

			result.append("\n\t");

			addTopLevelBiPurposeAnnotations(
					result,
					"",
					TypeReference.newFormalParameterReference(i).getValue(),
					paramInfo.signatureArrayDims == 0 ? null : TypePath.fromString("[".repeat(paramInfo.signatureArrayDims)),
					EnumSet.of(ElementType.PARAMETER),
					declaration.invisibleParameterAnnotations == null || i >= declaration.invisibleParameterAnnotations.length ? null : declaration.invisibleParameterAnnotations[i],
					declaration.visibleParameterAnnotations == null || i >= declaration.visibleParameterAnnotations.length ? null : declaration.visibleParameterAnnotations[i],
					declaration.invisibleTypeAnnotations,
					declaration.visibleTypeAnnotations,
					(create, isTypeAnnotation) -> {
						if (isTypeAnnotation) {
							return editor.getData();
						} else if (create) {
							return ((MethodAnnotationData) editor.getData()).parameters().computeIfAbsent(paramIndex, k -> new GenericAnnotationData());
						} else {
							return ((MethodAnnotationData) editor.getData()).parameters().get(paramIndex);
						}
					}
			);

			result.append(paramInfo.typeWithButtons);
			result.append(" ");
			result.append(paramInfo.variableName);
		}

		if (!parameterInfos.isEmpty()) {
			result.append("\n");
		}

		result.append(")");

		if (declaration.exceptions != null && !declaration.exceptions.isEmpty()) {
			result.append(" throws ");

			if (declaration.signature != null) {
				new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
					int exceptionIndex = -1;

					@Override
					public SignatureVisitor visitExceptionType() {
						exceptionIndex++;

						if (exceptionIndex != 0) {
							result.append(", ");
						}

						return new TypeRefAppender(result, TypeReference.newExceptionReference(exceptionIndex).getValue());
					}
				});
			} else {
				for (int i = 0; i < declaration.exceptions.size(); i++) {
					if (i != 0) {
						result.append(", ");
					}

					TypeRefAppender appender = new TypeRefAppender(result, TypeReference.newExceptionReference(i).getValue());
					appender.visitClassType(declaration.exceptions.get(i));
					appender.visitEnd();
				}
			}
		}

		result.append(";");
		return result;
	}

	private String generateVariableName(Type type) {
		return switch (type.getSort()) {
		case Type.ARRAY -> generateVariableName(type.getElementType()) + "Array".repeat(type.getDimensions());
		case Type.BOOLEAN -> "bl";
		case Type.BYTE -> "b";
		case Type.CHAR -> "c";
		case Type.SHORT -> "s";
		case Type.INT -> "i";
		case Type.LONG -> "l";
		case Type.FLOAT -> "f";
		case Type.DOUBLE -> "d";
		case Type.OBJECT -> {
			String simpleName = AnnotationUtil.getSimpleName(project.deobfuscate(ClassEntryView.create(type.getInternalName())).getFullName());
			simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);

			while (simpleName.isEmpty() || !Character.isJavaIdentifierStart(simpleName.charAt(0))) {
				simpleName = "_" + simpleName;
			}

			yield Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
		}
		default -> throw new IllegalStateException("Unexpected type: " + type.getSort());
		};
	}

	private void addTypeAnnotationButtons(TextWithButtons result, int typeRef, @Nullable TypePath typePath) {
		switch (editor.getDeclaration()) {
		case ClassNode classNode -> addTypeAnnotationButtons(result, typeRef, typePath, classNode.invisibleTypeAnnotations, classNode.visibleTypeAnnotations);
		case FieldNode fieldNode -> addTypeAnnotationButtons(result, typeRef, typePath, fieldNode.invisibleTypeAnnotations, fieldNode.visibleTypeAnnotations);
		case MethodNode methodNode -> addTypeAnnotationButtons(result, typeRef, typePath, methodNode.invisibleTypeAnnotations, methodNode.visibleTypeAnnotations);
		default -> throw new IllegalStateException("Unsupported declaration type: " + editor.getDeclaration().getClass().getName());
		}
	}

	private boolean appendTypeParameters(TextWithButtons result, @Nullable String signature, int paramRefSort, int paramBoundRefSort) {
		if (signature == null) {
			return false;
		}

		var visitor = new SignatureVisitor(Opcodes.ASM9) {
					int typeParameterIndex = -1;
					int boundIndex = -1;

					@Override
					public void visitFormalTypeParameter(String name) {
						typeParameterIndex++;

						if (typeParameterIndex == 0) {
							result.append("<");
						} else {
							result.append(", ");
						}

						addTypeAnnotationButtons(result, TypeReference.newTypeParameterReference(paramRefSort, typeParameterIndex).getValue(), null);

						result.append(name);

						boundIndex = -1;
					}

					@Override
					public SignatureVisitor visitClassBound() {
						boundIndex = 0;
						result.append(" extends ");
						return new TypeRefAppender(result, TypeReference.newTypeParameterBoundReference(paramBoundRefSort, typeParameterIndex, boundIndex).getValue());
					}

					@Override
					public SignatureVisitor visitInterfaceBound() {
						if (boundIndex == -1) {
							result.append(" extends ");
							boundIndex = 1;
						} else {
							result.append(" & ");
							boundIndex++;
						}

						return new TypeRefAppender(result, TypeReference.newTypeParameterBoundReference(paramBoundRefSort, typeParameterIndex, boundIndex).getValue());
					}

					@Override
					public void visitEnd() {
						if (typeParameterIndex >= 0) {
							result.append(">");
						}
					}
				};

		new SignatureReader(signature).accept(visitor);

		return visitor.typeParameterIndex >= 0;
	}

	private void addTopLevelAnnotations(
			TextWithButtons result,
			Set<ElementType> elementTypes,
			@Nullable List<AnnotationNode> invisibleAnnotations,
			@Nullable List<AnnotationNode> visibleAnnotations
	) {
		if (invisibleAnnotations != null) {
			for (AnnotationNode ann : invisibleAnnotations) {
				result.append(editor.createExistingAnnotationButton(List.of(ann)));
				result.append("\n");
			}
		}

		if (visibleAnnotations != null) {
			for (AnnotationNode ann : visibleAnnotations) {
				result.append(editor.createExistingAnnotationButton(List.of(ann)));
				result.append("\n");
			}
		}

		for (AnnotationNode ann : editor.getData().annotationsToAdd()) {
			result.append(editor.createAddedAnnotationButton(
					List.of(ann),
					desc1 -> List.of(new AnnotationNode(desc1)),
					annClass -> getAnnotationTargets(annClass).stream().anyMatch(elementTypes::contains)
			));
			result.append("\n");
		}

		result.append(editor.createPlusButton(
				desc -> List.of(new AnnotationNode(desc)),
				annClass -> getAnnotationTargets(annClass).stream().anyMatch(elementTypes::contains)
		));
		result.append("\n");
	}

	private void addTypeAnnotationButtons(
			TextWithButtons result,
			int typeRef,
			@Nullable TypePath typePath,
			@Nullable List<TypeAnnotationNode> invisibleAnnotations,
			@Nullable List<TypeAnnotationNode> visibleAnnotations
	) {
		if (invisibleAnnotations != null) {
			for (TypeAnnotationNode ann : invisibleAnnotations) {
				if (ann.typeRef == typeRef && AnnotationUtil.typePathToString(ann.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					result.append(editor.createExistingAnnotationButton(List.of(ann)));
				}
			}
		}

		if (visibleAnnotations != null) {
			for (TypeAnnotationNode ann : visibleAnnotations) {
				if (ann.typeRef == typeRef && AnnotationUtil.typePathToString(ann.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					result.append(editor.createExistingAnnotationButton(List.of(ann)));
				}
			}
		}

		for (TypeAnnotationNode ann : editor.getData().typeAnnotationsToAdd()) {
			if (ann.typeRef == typeRef && AnnotationUtil.typePathToString(ann.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
				result.append(editor.createAddedAnnotationButton(
						List.of(ann),
						desc1 -> List.of(new TypeAnnotationNode(typeRef, typePath, desc1)),
						annClass -> getAnnotationTargets(annClass).contains(ElementType.TYPE_USE)
				));
			}
		}

		result.append(editor.createPlusButton(
				desc -> List.of(new TypeAnnotationNode(typeRef, typePath, desc)),
				annClass -> getAnnotationTargets(annClass).contains(ElementType.TYPE_USE)
		));
	}

	private void addTopLevelBiPurposeAnnotations(
			TextWithButtons result,
			String separator,
			int typeRef,
			@Nullable TypePath typePath,
			Set<ElementType> topLevelElementTypes,
			@Nullable List<AnnotationNode> invisibleAnnotations,
			@Nullable List<AnnotationNode> visibleAnnotations,
			@Nullable List<TypeAnnotationNode> invisibleTypeAnnotations,
			@Nullable List<TypeAnnotationNode> visibleTypeAnnotations,
			AnnotationsEditor.AnnotationDataSupplier dataSupplier
	) {
		Map<String, List<AnnotationNode>> existingMap = new LinkedHashMap<>();

		if (invisibleAnnotations != null) {
			for (AnnotationNode annotation : invisibleAnnotations) {
				existingMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
			}
		}

		if (visibleAnnotations != null) {
			for (AnnotationNode annotation : visibleAnnotations) {
				existingMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
			}
		}

		if (invisibleTypeAnnotations != null) {
			for (TypeAnnotationNode annotation : invisibleTypeAnnotations) {
				if (annotation.typeRef == typeRef && AnnotationUtil.typePathToString(annotation.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					existingMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
				}
			}
		}

		if (visibleTypeAnnotations != null) {
			for (TypeAnnotationNode annotation : visibleTypeAnnotations) {
				if (annotation.typeRef == typeRef && AnnotationUtil.typePathToString(annotation.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					existingMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
				}
			}
		}

		Map<String, List<AnnotationNode>> addedMap = new LinkedHashMap<>();

		BaseAnnotationData data = dataSupplier.get(false, false);

		if (data != null) {
			for (AnnotationNode annotation : data.annotationsToAdd()) {
				addedMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
			}
		}

		BaseAnnotationData typeData = dataSupplier.get(false, true);

		if (typeData != null) {
			for (TypeAnnotationNode annotation : typeData.typeAnnotationsToAdd()) {
				if (annotation.typeRef == typeRef && AnnotationUtil.typePathToString(annotation.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					addedMap.computeIfAbsent(annotation.desc, k -> new ArrayList<>()).add(annotation);
				}
			}
		}

		for (List<AnnotationNode> annotationGroup : existingMap.values()) {
			result.append(editor.createExistingAnnotationButton(dataSupplier, annotationGroup));
			result.append(separator);
		}

		for (List<AnnotationNode> annotationGroup : addedMap.values()) {
			result.append(editor.createAddedAnnotationButton(
					dataSupplier,
					annotationGroup,
					desc -> createBiPurposeAnnotations(desc, typeRef, typePath, topLevelElementTypes),
					annClass -> getAnnotationTargets(annClass).stream().anyMatch(target -> target == ElementType.TYPE_USE || topLevelElementTypes.contains(target))
			));
			result.append(separator);
		}

		result.append(editor.createPlusButton(
				dataSupplier,
				desc -> createBiPurposeAnnotations(desc, typeRef, typePath, topLevelElementTypes),
				annClass -> getAnnotationTargets(annClass).stream().anyMatch(target -> target == ElementType.TYPE_USE || topLevelElementTypes.contains(target))
		));
		result.append(separator);
	}

	private List<AnnotationNode> createBiPurposeAnnotations(
			String desc,
			int typeRef,
			@Nullable TypePath typePath,
			Set<ElementType> topLevelElementTypes
	) {
		String deobfName = desc.substring(1, desc.length() - 1);
		String obfName = project.obfuscate(ClassEntryView.create(deobfName)).getFullName();
		ClassNode bytecode = project.getBytecode(obfName);

		if (bytecode == null) {
			return List.of(new AnnotationNode(desc));
		}

		List<ElementType> elementTypes = getAnnotationTargets(bytecode);

		List<AnnotationNode> result = new ArrayList<>();

		if (elementTypes.stream().anyMatch(topLevelElementTypes::contains)) {
			result.add(new AnnotationNode(desc));
		}

		if (elementTypes.contains(ElementType.TYPE_USE)) {
			result.add(new TypeAnnotationNode(typeRef, typePath, desc));
		}

		// fallback
		if (result.isEmpty()) {
			result.add(new AnnotationNode(desc));
		}

		return result;
	}

	private static List<ElementType> getAnnotationTargets(ClassNode annotationClass) {
		List<ElementType> elementTypes = new ArrayList<>();

		if (annotationClass.visibleAnnotations == null) {
			return elementTypes;
		}

		for (AnnotationNode metaAnnotation : annotationClass.visibleAnnotations) {
			if (!"Ljava/lang/annotation/Target;".equals(metaAnnotation.desc) || metaAnnotation.values == null) {
				continue;
			}

			for (int i = 0; i < metaAnnotation.values.size(); i += 2) {
				if (!"value".equals(metaAnnotation.values.get(i)) || !(metaAnnotation.values.get(i + 1) instanceof List<?> values)) {
					continue;
				}

				for (Object value : values) {
					if (!(value instanceof String[] enumValue)) {
						continue;
					}

					try {
						elementTypes.add(ElementType.valueOf(enumValue[1]));
					} catch (IllegalArgumentException e) {
						// ignore
					}
				}
			}
		}

		return elementTypes;
	}

	private class TypeRefAppender extends SignatureVisitor {
		private final TextWithButtons result;
		private final int typeRef;
		@Nullable
		private TypePath typePath;
		private String classSoFar;
		private boolean isArray = false;
		private int typeArgumentIndex = -1;
		private final boolean createLeading;

		TypeRefAppender(TextWithButtons result, int typeRef) {
			this(result, typeRef, true);
		}

		TypeRefAppender(TextWithButtons result, int typeRef, boolean createLeading) {
			this(result, typeRef, null, createLeading);
		}

		TypeRefAppender(TextWithButtons result, int typeRef, @Nullable TypePath typePath, boolean createLeading) {
			super(Opcodes.ASM9);
			this.result = result;
			this.typeRef = typeRef;
			this.typePath = typePath;
			this.createLeading = createLeading;
		}

		private TypePath nextTypePath(String step) {
			return typePath == null ? TypePath.fromString(step) : TypePath.fromString(typePath + step);
		}

		@Override
		public void visitClassType(String name) {
			String[] innerParts = name.split("\\$");

			if (createLeading) {
				addTypeAnnotationButtons();
			}

			String deobfName = project.deobfuscate(ClassEntryView.create(innerParts[0])).getFullName();
			result.append(AnnotationUtil.getSimpleName(deobfName));

			classSoFar = innerParts[0] + "$";

			for (int i = 1; i < innerParts.length; i++) {
				visitInnerClassType(innerParts[i]);
			}
		}

		@Override
		public SignatureVisitor visitArrayType() {
			isArray = true;
			return new TypeRefAppender(result, typeRef, nextTypePath("["), createLeading) {
				@Override
				public void visitEnd() {
					super.visitEnd();
					TypeRefAppender.this.visitEnd();
				}
			};
		}

		@Override
		public void visitBaseType(char descriptor) {
			if (createLeading) {
				addTypeAnnotationButtons();
			}

			result.append(Type.getType(String.valueOf(descriptor)).getClassName());
			visitEnd();
		}

		@Override
		public void visitTypeVariable(String name) {
			if (createLeading) {
				addTypeAnnotationButtons();
			}

			result.append(name);
			visitEnd();
		}

		@Override
		public void visitInnerClassType(String name) {
			result.append(".");
			typePath = nextTypePath(".");
			String deobfName = project.deobfuscate(ClassEntryView.create(classSoFar + name)).getFullName();
			addTypeAnnotationButtons();
			result.append(deobfName.substring(deobfName.lastIndexOf('$') + 1));
			classSoFar += name + "$";
		}

		@Override
		public void visitTypeArgument() {
			typeArgumentIndex++;

			if (typeArgumentIndex == 0) {
				result.append("<");
			} else {
				result.append(", ");
			}

			TypePath prevTypePath = typePath;
			typePath = nextTypePath(typeArgumentIndex + ";");
			addTypeAnnotationButtons();
			result.append("?");
			typePath = prevTypePath;
		}

		@Override
		public SignatureVisitor visitTypeArgument(char wildcard) {
			typeArgumentIndex++;

			if (typeArgumentIndex == 0) {
				result.append("<");
			} else {
				result.append(", ");
			}

			TypePath prevTypePath = typePath;
			typePath = nextTypePath(typeArgumentIndex + ";");
			SignatureVisitor innerVisitor = switch (wildcard) {
			case EXTENDS -> {
				addTypeAnnotationButtons();
				result.append("? extends ");
				yield new TypeRefAppender(result, typeRef, nextTypePath("*"), true);
			}
			case SUPER -> {
				addTypeAnnotationButtons();
				result.append("? super ");
				yield new TypeRefAppender(result, typeRef, nextTypePath("*"), true);
			}
			case INSTANCEOF -> new TypeRefAppender(result, typeRef, typePath, true);
			default -> throw new IllegalStateException("Unsupported wildcard type: " + wildcard);
			};
			typePath = prevTypePath;
			return innerVisitor;
		}

		@Override
		public void visitEnd() {
			if (isArray) {
				addTypeAnnotationButtons();
				result.append("[]");
			} else if (typeArgumentIndex >= 0) {
				result.append(">");
			}
		}

		private void addTypeAnnotationButtons() {
			DeclarationGenerator.this.addTypeAnnotationButtons(result, typeRef, typePath);
		}
	}
}
