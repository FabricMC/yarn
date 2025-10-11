package net.fabricmc.filament.enigma.annotations.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

		appendTopLevelAnnotations(declaration.visibleAnnotations, declaration.invisibleAnnotations, result);

		ClassDeclType declType = ClassDeclType.infer(declaration);

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

		appendTopLevelAnnotations(declaration.visibleAnnotations, declaration.invisibleAnnotations, result);

		new SignatureReader(Objects.requireNonNullElse(declaration.signature, declaration.desc))
				.acceptType(new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.FIELD).getValue()));
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

		appendTopLevelAnnotations(declaration.visibleAnnotations, declaration.invisibleAnnotations, result);

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
						return new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue());
					}
				});
			} else {
				new SignatureReader(Type.getReturnType(declaration.desc).getDescriptor())
						.acceptType(new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue()));
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

		Set<String> usedVariableNames = new HashSet<>();
		List<String> variableNames = new ArrayList<>();
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
				variableNames.add(variableName);
			}

			lvIndex += paramTypes[i].getSize();
		}

		if (declaration.signature != null) {
			var visitor = new SignatureVisitor(Opcodes.ASM9) {
				int paramIndex = -1;

				@Override
				public SignatureVisitor visitParameterType() {
					// append the previous variable name
					if (paramIndex >= 0 && paramIndex < variableNames.size()) {
						result.append(variableNames.get(paramIndex));
					}

					paramIndex++;

					if (paramIndex != 0 || hasThisParam) {
						result.append(",");
					}

					result.append("\n\t");
					return new TypeRefAppender(result, TypeReference.newFormalParameterReference(paramIndex).getValue());
				}
			};
			new SignatureReader(declaration.signature).accept(visitor);

			if (visitor.paramIndex >= 0) {
				// append the last variable name
				if (visitor.paramIndex < variableNames.size()) {
					result.append(variableNames.get(visitor.paramIndex));
				}

				result.append("\n");
			}
		} else {
			int nonSyntheticParamIndex = -1;

			for (int i = 0; i < paramTypes.length; i++) {
				if (i >= params.size() || (params.get(i).access & Opcodes.ACC_SYNTHETIC) == 0) {
					nonSyntheticParamIndex++;

					if (nonSyntheticParamIndex != 0 || hasThisParam) {
						result.append(",");
					}

					result.append("\n\t");
					new SignatureReader(paramTypes[i].getDescriptor())
							.acceptType(new TypeRefAppender(result, TypeReference.newFormalParameterReference(nonSyntheticParamIndex).getValue()));
					result.append(" ");

					if (nonSyntheticParamIndex < variableNames.size()) {
						result.append(variableNames.get(nonSyntheticParamIndex));
					}
				}
			}

			if (nonSyntheticParamIndex >= 0) {
				result.append("\n");
			}
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

	private void appendTopLevelAnnotations(
			@Nullable List<AnnotationNode> visibleAnnotations,
			@Nullable List<AnnotationNode> invisibleAnnotations,
			TextWithButtons result
	) {
		if (invisibleAnnotations != null) {
			for (AnnotationNode ann : invisibleAnnotations) {
				result.append(editor.createExistingAnnotationButton(ann));
				result.append("\n");
			}
		}

		if (visibleAnnotations != null) {
			for (AnnotationNode ann : visibleAnnotations) {
				result.append(editor.createExistingAnnotationButton(ann));
				result.append("\n");
			}
		}

		for (AnnotationNode ann : editor.getData().annotationsToAdd()) {
			result.append(editor.createAddedAnnotationButton(ann));
			result.append("\n");
		}

		result.append(editor.createPlusButton(AnnotationNode::new));
		result.append("\n");
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
					result.append(editor.createExistingAnnotationButton(ann));
				}
			}
		}

		if (visibleAnnotations != null) {
			for (TypeAnnotationNode ann : visibleAnnotations) {
				if (ann.typeRef == typeRef && AnnotationUtil.typePathToString(ann.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
					result.append(editor.createExistingAnnotationButton(ann));
				}
			}
		}

		for (TypeAnnotationNode ann : editor.getData().typeAnnotationsToAdd()) {
			if (ann.typeRef == typeRef && AnnotationUtil.typePathToString(ann.typePath).equals(AnnotationUtil.typePathToString(typePath))) {
				result.append(editor.createAddedAnnotationButton(ann));
			}
		}

		result.append(editor.createPlusButton(desc -> new TypeAnnotationNode(typeRef, typePath, desc)));
	}

	private class TypeRefAppender extends SignatureVisitor {
		private final TextWithButtons result;
		private final int typeRef;
		@Nullable
		private TypePath typePath;
		private String classSoFar;
		private boolean isArray = false;
		private int typeArgumentIndex = -1;

		TypeRefAppender(TextWithButtons result, int typeRef) {
			this(result, typeRef, null);
		}

		TypeRefAppender(TextWithButtons result, int typeRef, @Nullable TypePath typePath) {
			super(Opcodes.ASM9);
			this.result = result;
			this.typeRef = typeRef;
			this.typePath = typePath;
		}

		private TypePath nextTypePath(String step) {
			return typePath == null ? TypePath.fromString(step) : TypePath.fromString(typePath + step);
		}

		@Override
		public void visitClassType(String name) {
			String[] innerParts = name.split("\\$");

			addTypeAnnotationButtons();

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
			return new TypeRefAppender(result, typeRef, nextTypePath("["));
		}

		@Override
		public void visitBaseType(char descriptor) {
			addTypeAnnotationButtons();
			result.append(Type.getType(String.valueOf(descriptor)).getClassName());
		}

		@Override
		public void visitTypeVariable(String name) {
			addTypeAnnotationButtons();
			result.append(name);
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
				yield new TypeRefAppender(result, typeRef, nextTypePath("*"));
			}
			case SUPER -> {
				addTypeAnnotationButtons();
				result.append("? super ");
				yield new TypeRefAppender(result, typeRef, nextTypePath("*"));
			}
			case INSTANCEOF -> new TypeRefAppender(result, typeRef, typePath);
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
