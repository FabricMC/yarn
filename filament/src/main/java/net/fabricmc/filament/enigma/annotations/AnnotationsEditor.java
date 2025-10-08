package net.fabricmc.filament.enigma.annotations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cuchaz.enigma.api.DataInvalidationEvent;
import cuchaz.enigma.api.I18n;
import cuchaz.enigma.api.view.GuiView;
import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.EntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.BaseAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.TypeAnnotationKey;
import net.fabricmc.loom.util.Pair;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class AnnotationsEditor extends JDialog {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final Comparator<TypeAnnotationKey> TYPE_ANNOTATION_KEY_COMPARATOR = Comparator.comparing(TypeAnnotationKey::name)
			.thenComparingInt(TypeAnnotationKey::typeRef)
			.thenComparing(TypeAnnotationKey::typePath);

	private final AnnotationsEnigmaPlugin plugin;
	private final ProjectView project;
	private final Object declaration;
	private final EntryView editingEntry;
	private final BaseAnnotationData data;
	private final JEditorPane editor;
	private final JLayeredPane layeredPane;
	private final JScrollPane scrollPane;

	private AnnotationsEditor(GuiView gui, AnnotationsEnigmaPlugin plugin, ProjectView project, EntryView editingEntry, Object declaration) {
		super(gui.getFrame(), I18n.translate("annotations.edit"), true);

		this.plugin = plugin;
		this.project = project;
		this.declaration = declaration;
		this.editingEntry = editingEntry;
		this.data = getEditingData(editingEntry);

		setModalityType(ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		editor = gui.createEditorPane();
		editor.setFocusable(false);
		editor.setRequestFocusEnabled(false);
		layeredPane = new ScrollableLayeredPane(editor);
		scrollPane = new JScrollPane(layeredPane);

		setSize((int) (600 * gui.getScale()), (int) (300 * gui.getScale()));
		setLocationRelativeTo(gui.getFrame());

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		JButton saveButton = new JButton(I18n.translate("prompt.save"));
		buttonPanel.add(saveButton);
		saveButton.addActionListener(e -> {
			saveData();
			dispose();
		});

		JButton cancelButton = new JButton(I18n.translate("prompt.cancel"));
		buttonPanel.add(cancelButton);
		cancelButton.addActionListener(e -> dispose());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				refreshUi();
			}
		});
	}

	public static void open(GuiView gui, AnnotationsEnigmaPlugin plugin, EntryView editingEntry) {
		ProjectView project = plugin.project;

		if (project == null) {
			return;
		}

		Object declaration = getDeclaration(project, editingEntry);

		if (declaration == null) {
			return;
		}

		AnnotationsEditor editor = new AnnotationsEditor(gui, plugin, project, editingEntry, declaration);
		editor.setVisible(true);
	}

	@Nullable
	private static Object getDeclaration(ProjectView project, EntryView entry) {
		return switch (entry) {
			case ClassEntryView classEntry -> project.getBytecode(classEntry.getFullName());
			case FieldEntryView fieldEntry -> {
				ClassNode bytecode = project.getBytecode(fieldEntry.getParent().getFullName());

				if (bytecode == null) {
					yield null;
				}

				yield bytecode.fields.stream()
						.filter(field -> field.name.equals(fieldEntry.getName()) && field.desc.equals(fieldEntry.getDescriptor()))
						.findFirst()
						.orElse(null);
			}
			case MethodEntryView methodEntry -> {
				ClassNode bytecode = project.getBytecode(methodEntry.getParent().getFullName());

				if (bytecode == null) {
					yield null;
				}

				yield bytecode.methods.stream()
						.filter(method -> method.name.equals(methodEntry.getName()) && method.desc.equals(methodEntry.getDescriptor()))
						.findFirst()
						.orElse(null);
			}
			default -> throw new IllegalArgumentException("Unsupported entry type: " + entry.getClass().getName());
		};
	}

	private BaseAnnotationData getEditingData(EntryView editingEntry) {
		EntryView deobf = project.deobfuscate(editingEntry);

		return switch (deobf) {
			case ClassEntryView ignored -> {
				ClassAnnotationData data = plugin.data.classes().get(deobf.getFullName());
				yield data == null ? new ClassAnnotationData() : new ClassAnnotationData(data);
			}
			case FieldEntryView fieldEntry -> Objects.requireNonNullElseGet(
					((ClassAnnotationData) getEditingData(fieldEntry.getParent())).getFieldData(fieldEntry.getName(), fieldEntry.getDescriptor()),
					GenericAnnotationData::new
			);
			case MethodEntryView methodEntry -> Objects.requireNonNullElseGet(
					((ClassAnnotationData) getEditingData(methodEntry.getParent())).getMethodData(methodEntry.getName(), methodEntry.getDescriptor()),
					MethodAnnotationData::new
			);
			default -> throw new IllegalArgumentException("Unsupported entry type: " + deobf.getClass().getName());
		};
	}

	private static boolean isEmpty(BaseAnnotationData data) {
		if (!data.annotationsToRemove().isEmpty() && !data.annotationsToAdd().isEmpty()) {
			return false;
		}

		if (!data.typeAnnotationsToRemove().isEmpty() && !data.typeAnnotationsToAdd().isEmpty()) {
			return false;
		}

		return switch (data) {
			case ClassAnnotationData classData -> classData.fields().isEmpty() && classData.methods().isEmpty();
			case MethodAnnotationData methodData -> methodData.parameters().values().stream().allMatch(AnnotationsEditor::isEmpty);
			default -> true;
		};
	}

	private void saveData() {
		EntryView deobf = project.deobfuscate(editingEntry);

		boolean changed;

		if (isEmpty(data)) {
			changed = switch (deobf) {
				case ClassEntryView ignored -> plugin.data.classes().remove(deobf.getFullName()) != null;
				case FieldEntryView fieldEntry -> {
					ClassAnnotationData classData = plugin.data.classes().get(fieldEntry.getParent().getFullName());
					boolean res = classData != null && classData.fields().remove(fieldEntry.getName() + ":" + fieldEntry.getDescriptor()) != null;

					if (res && isEmpty(classData)) {
						plugin.data.classes().remove(fieldEntry.getParent().getFullName());
					}

					yield res;
				}
				case MethodEntryView methodEntry -> {
					ClassAnnotationData classData = plugin.data.classes().get(methodEntry.getParent().getFullName());
					boolean res = classData != null && classData.methods().remove(methodEntry.getName() + methodEntry.getDescriptor()) != null;

					if (res && isEmpty(classData)) {
						plugin.data.classes().remove(methodEntry.getParent().getFullName());
					}

					yield res;
				}
				default -> throw new IllegalArgumentException("Unsupported entry type: " + deobf.getClass().getName());
			};
		} else {
			changed = switch (deobf) {
				case ClassEntryView ignored -> {
					boolean res = !data.equals(plugin.data.classes().get(deobf.getFullName()));

					if (res) {
						addSorted(plugin.data.classes(), deobf.getFullName(), (ClassAnnotationData) data);
					}

					yield res;
				}
				case FieldEntryView fieldEntry -> {
					ClassAnnotationData classData = plugin.data.classes().get(fieldEntry.getParent().getFullName());

					if (classData == null) {
						classData = new ClassAnnotationData();
						addSorted(plugin.data.classes(), deobf.getFullName(), classData);
					}

					boolean res = !data.equals(classData.getFieldData(fieldEntry.getName(), fieldEntry.getDescriptor()));

					if (res) {
						addSorted(classData.fields(), fieldEntry.getName() + ":" + fieldEntry.getDescriptor(), (GenericAnnotationData) data);
					}

					yield res;
				}
				case MethodEntryView methodEntry -> {
					ClassAnnotationData classData = plugin.data.classes().get(methodEntry.getParent().getFullName());

					if (classData == null) {
						classData = new ClassAnnotationData();
						addSorted(plugin.data.classes(), deobf.getFullName(), classData);
					}

					boolean res = !data.equals(classData.getMethodData(methodEntry.getName(), methodEntry.getDescriptor()));

					if (res) {
						addSorted(classData.methods(), methodEntry.getName() + methodEntry.getDescriptor(), (MethodAnnotationData) data);
					}

					yield res;
				}
				default -> throw new IllegalArgumentException("Unsupported entry type: " + deobf.getClass().getName());
			};
		}

		if (changed) {
			try (BufferedWriter writer = Files.newBufferedWriter(plugin.dataPath)) {
				GSON.toJson(plugin.data.toJson(), writer);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Error saving annotations", JOptionPane.ERROR_MESSAGE);
			}

			ClassEntryView invalidatingClass = switch (editingEntry) {
				case ClassEntryView classEntry -> classEntry;
				case FieldEntryView fieldEntry -> fieldEntry.getParent();
				case MethodEntryView methodEntry -> methodEntry.getParent();
				default -> throw new IllegalArgumentException("Unsupported entry type: " + editingEntry.getClass().getName());
			};

			project.invalidateData(invalidatingClass.getFullName(), DataInvalidationEvent.InvalidationType.DECOMPILE);
		}
	}

	private void refreshUi() {
		editor.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());

		for (Component oldButton : layeredPane.getComponentsInLayer(JLayeredPane.PALETTE_LAYER)) {
			layeredPane.remove(oldButton);
		}

		TextWithButtons textWithButtons = buildDeclarationUi();

		textWithButtons.buttons.forEach((index, buttons) -> buttons.forEach(button -> layeredPane.add(button, JLayeredPane.PALETTE_LAYER)));

		int offset = 0;

		editor.setText(textWithButtons.text.toString());
		FontMetrics metrics = editor.getFontMetrics(editor.getFont());

		for (Map.Entry<Integer, List<JButton>> buttonEntry : textWithButtons.buttons.entrySet()) {
			int index = buttonEntry.getKey() + offset;
			List<JButton> buttons = buttonEntry.getValue();
			Rectangle2D buttonsLocation;

			try {
				buttonsLocation = editor.modelToView2D(index);
			} catch (BadLocationException e) {
				throw new AssertionError(e);
			}

			int x = (int) buttonsLocation.getX();
			int y = (int) buttonsLocation.getY();

			for (JButton button : buttons) {
				button.setFont(editor.getFont());
				button.setBounds(x, y, metrics.stringWidth(button.getText()) + button.getInsets().left + button.getInsets().right, metrics.getHeight());
				x += button.getWidth();
			}

			int buttonWidth = x - (int) buttonsLocation.getX();
			int spaceWidth = metrics.stringWidth(" ");
			int spaceCount = (buttonWidth + spaceWidth - 1) / spaceWidth;

			try {
				editor.getDocument().insertString(index, " ".repeat(spaceCount), null);
			} catch (BadLocationException e) {
				throw new AssertionError(e);
			}

			offset += spaceCount;
		}

		Dimension preferredSize = editor.getPreferredSize();
		layeredPane.setSize(Math.max(preferredSize.width, scrollPane.getViewport().getWidth()), Math.max(preferredSize.height, scrollPane.getViewport().getHeight()));
		editor.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
	}

	private TextWithButtons buildDeclarationUi() {
		return switch (declaration) {
			case ClassNode classNode -> buildDeclarationUi(classNode);
			case FieldNode fieldNode -> buildDeclarationUi(fieldNode);
			case MethodNode methodNode -> buildDeclarationUi(methodNode);
			default -> throw new IllegalStateException("Unsupported declaration type: " + declaration.getClass().getName());
		};
	}

	private TextWithButtons buildDeclarationUi(ClassNode declaration) {
		TextWithButtons result = new TextWithButtons();

		if (declaration.invisibleAnnotations != null) {
			for (AnnotationNode ann : declaration.invisibleAnnotations) {
				result.append(createAnnotationButton(ann));
				result.append("\n");
			}
		}

		if (declaration.visibleAnnotations != null) {
			for (AnnotationNode ann : declaration.visibleAnnotations) {
				result.append(createAnnotationButton(ann));
				result.append("\n");
			}
		}

		result.append(createPlusButton());
		result.append("\n");

		ClassDeclType declType = ClassDeclType.infer(declaration);

		result.append(declType.keyword);
		result.append(" ");
		result.append(getSimpleName(project.deobfuscate(ClassEntryView.create(declaration.name)).getFullName()));

		appendTypeParameters(result, declaration.signature, TypeReference.CLASS_TYPE_PARAMETER, TypeReference.CLASS_TYPE_PARAMETER_BOUND);

		if (declType == ClassDeclType.CLASS && declaration.superName != null) {
			result.append(" extends ");

			if (declaration.signature != null) {
				new SignatureReader(declaration.signature).accept(new SignatureVisitor(Opcodes.ASM9) {
					@Override
					public SignatureVisitor visitSuperclass() {
						return new TypeRefAppender(result, TypeReference.newTypeReference(TypeReference.CLASS_EXTENDS).getValue());
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
						result.append(createPlusButton());
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

		return result;
	}

	private void appendTypeParameters(TextWithButtons result, @Nullable String signature, int paramRefSort, int paramBoundRefSort) {
		if (signature == null) {
			return;
		}

		new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
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
		});
	}

	private TextWithButtons buildDeclarationUi(FieldNode declaration) {
		// TODO
		return new TextWithButtons();
	}

	private TextWithButtons buildDeclarationUi(MethodNode declaration) {
		// TODO
		return new TextWithButtons();
	}

	private JButton createAnnotationButton(AnnotationNode annotation) {
		String annotationName = annotation.desc.substring(1, annotation.desc.length() - 1);
		String deobfName = project.deobfuscate(ClassEntryView.create(annotationName)).getFullName();
		StrikeableButton button = new StrikeableButton("@" + getSimpleName(deobfName));

		if (annotation instanceof TypeAnnotationNode typeAnnotation) {
			TypeAnnotationKey key = new TypeAnnotationKey(typeAnnotation.typeRef, typeAnnotation.typePath == null ? "" : typeAnnotation.typePath.toString(), annotationName);
			boolean[] isRemoved = { data.typeAnnotationsToRemove().contains(key) };
			button.setStrikethrough(isRemoved[0]);
			button.addActionListener(e -> {
				if (isRemoved[0]) {
					isRemoved[0] = false;
					data.typeAnnotationsToRemove().remove(key);
				} else {
					isRemoved[0] = true;
					addSorted(data.typeAnnotationsToRemove(), key, TYPE_ANNOTATION_KEY_COMPARATOR);
				}

				button.setStrikethrough(isRemoved[0]);
			});
		} else {
			boolean[] isRemoved = { data.annotationsToRemove().contains(annotationName) };
			button.setStrikethrough(isRemoved[0]);
			button.addActionListener(e -> {
				if (isRemoved[0]) {
					isRemoved[0] = false;
					data.annotationsToRemove().remove(annotationName);
				} else {
					isRemoved[0] = true;
					addSorted(data.annotationsToRemove(), annotationName);
				}

				button.setStrikethrough(isRemoved[0]);
			});
		}

		return button;
	}

	private JButton createPlusButton() {
		return new JButton("+");
	}

	private static String getSimpleName(String internalName) {
		int slashIndex = internalName.lastIndexOf('/');
		String simpleName = internalName.substring(slashIndex + 1);
		return simpleName.replace('$', '.');
	}

	private void addTypeAnnotationButtons(TextWithButtons result, int typeRef, @Nullable TypePath typePath) {
		switch (declaration) {
			case ClassNode classNode -> addTypeAnnotationButtons(result, typeRef, typePath, classNode.invisibleTypeAnnotations, classNode.visibleTypeAnnotations);
			case FieldNode fieldNode -> addTypeAnnotationButtons(result, typeRef, typePath, fieldNode.invisibleTypeAnnotations, fieldNode.visibleTypeAnnotations);
			case MethodNode methodNode -> addTypeAnnotationButtons(result, typeRef, typePath, methodNode.invisibleTypeAnnotations, methodNode.visibleTypeAnnotations);
			default -> throw new IllegalStateException("Unsupported declaration type: " + declaration.getClass().getName());
		}
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
				if (ann.typeRef == typeRef && Objects.equals(ann.typePath, typePath)) {
					result.append(createAnnotationButton(ann));
				}
			}
		}

		if (visibleAnnotations != null) {
			for (TypeAnnotationNode ann : visibleAnnotations) {
				if (ann.typeRef == typeRef && Objects.equals(ann.typePath, typePath)) {
					result.append(createAnnotationButton(ann));
				}
			}
		}

		result.append(createPlusButton());
	}

	private static <E extends Comparable<E>> void addSorted(Set<E> set, E value) {
		addSorted(set, value, Comparator.naturalOrder());
	}

	private static <E> void addSorted(Set<E> set, E value, Comparator<E> comp) {
		List<E> list = new ArrayList<>(set);
		list.sort(comp);
		int index = Collections.binarySearch(list, value, comp);

		if (index < 0) {
			list.add(-index - 1, value);
		} else {
			list.set(index, value);
		}

		set.clear();
		set.addAll(list);
	}

	private static <K extends Comparable<K>, V> void addSorted(Map<K, V> map, K key, V value) {
		List<Pair<K, V>> entries = new ArrayList<>(map.size() + 1);
		map.forEach((k, v) -> entries.add(new Pair<>(key, v)));
		entries.sort(Comparator.comparing(Pair::left));

		Pair<K, V> newEntry = new Pair<>(key, value);
		int index = Collections.binarySearch(entries, newEntry, Comparator.comparing(Pair::left));

		if (index < 0) {
			entries.add(-index - 1, newEntry);
		} else {
			entries.set(index, newEntry);
		}

		map.clear();

		for (Pair<K, V> entry : entries) {
			map.put(entry.left(), entry.right());
		}
	}

	private enum ClassDeclType {
		CLASS("class"),
		INTERFACE("interface"),
		ENUM("enum"),
		ANNOTATION("@interface"),
		RECORD("record"),
		;

		private final String keyword;

		ClassDeclType(String keyword) {
			this.keyword = keyword;
		}

		boolean isInterface() {
			return this == INTERFACE || this == ANNOTATION;
		}

		static ClassDeclType infer(ClassNode classNode) {
			return switch (classNode.superName) {
				case "java/lang/Enum" -> ENUM;
				case "java/lang/Record" -> RECORD;
				case null -> CLASS;
				default -> {
					if (classNode.interfaces != null && classNode.interfaces.contains("java/lang/annotation/Annotation")) {
						yield ANNOTATION;
					} else if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
						yield INTERFACE;
					} else {
						yield CLASS;
					}
				}
			};
		}
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
			result.append(getSimpleName(deobfName));

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
			result.append(org.objectweb.asm.Type.getType(String.valueOf(descriptor)).getClassName());
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
			AnnotationsEditor.this.addTypeAnnotationButtons(result, typeRef, typePath);
		}
	}

	private record TextWithButtons(StringBuilder text, TreeMap<Integer, List<JButton>> buttons) {
		TextWithButtons() {
			this(new StringBuilder(), new TreeMap<>());
		}

		void append(String text) {
			this.text.append(text);
		}

		void append(JButton button) {
			this.buttons.computeIfAbsent(this.text.length(), k -> new ArrayList<>(1)).add(button);
		}
	}

	private static class StrikeableButton extends JButton {
		private boolean strikethrough;

		public StrikeableButton(String text) {
			super(text);
		}

		public void setStrikethrough(boolean strikethrough) {
			if (strikethrough != this.strikethrough) {
				this.strikethrough = strikethrough;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			if (strikethrough) {
				Insets margin = getMargin();
				int middle = (margin.top + getHeight() - margin.bottom) / 2;
				g.drawLine(margin.left, middle, getWidth() - margin.right, middle);
			}
		}
	}

	private static class ScrollableLayeredPane extends JLayeredPane implements Scrollable {
		private final JEditorPane editor;

		ScrollableLayeredPane(JEditorPane editor) {
			this.editor = editor;
			setLayout(null);
			add(editor, DEFAULT_LAYER);
		}

		@Override
		public Dimension getPreferredSize() {
			return editor.getPreferredSize();
		}

		@Override
		public Dimension getMinimumSize() {
			return editor.getMinimumSize();
		}

		@Override
		public Dimension getMaximumSize() {
			return editor.getMaximumSize();
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return editor.getScrollableUnitIncrement(visibleRect, orientation, direction);
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return editor.getScrollableBlockIncrement(visibleRect, orientation, direction);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			if (SwingUtilities.getUnwrappedParent(this) instanceof JViewport port) {
				TextUI ui = editor.getUI();
				int w = port.getWidth();
				Dimension min = ui.getMinimumSize(editor);

				if (w >= min.width) {
					Dimension max = ui.getMaximumSize(editor);

					if (w <= max.width) {
						return true;
					}
				}
			}

			return false;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			if (SwingUtilities.getUnwrappedParent(this) instanceof JViewport port) {
				TextUI ui = editor.getUI();
				int h = port.getHeight();
				Dimension min = ui.getMinimumSize(editor);

				if (h >= min.height) {
					Dimension max = ui.getMaximumSize(editor);

					if (h <= max.height) {
						return true;
					}
				}
			}

			return false;
		}
	}
}
