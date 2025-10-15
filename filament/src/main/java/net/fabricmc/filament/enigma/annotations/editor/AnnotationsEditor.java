package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
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
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.BadLocationException;

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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.filament.enigma.annotations.AnnotationUtil;
import net.fabricmc.filament.enigma.annotations.AnnotationsEnigmaPlugin;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.BaseAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.TypeAnnotationKey;
import net.fabricmc.loom.util.Pair;

public class AnnotationsEditor extends JDialog {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final Comparator<TypeAnnotationKey> TYPE_ANNOTATION_KEY_COMPARATOR = Comparator.comparing(TypeAnnotationKey::name)
			.thenComparingInt(TypeAnnotationKey::typeRef)
			.thenComparing(TypeAnnotationKey::typePath);
	private static final Comparator<AnnotationNode> ANNOTATION_COMPARATOR = Comparator.comparing(ann -> ann.desc);
	private static final Comparator<TypeAnnotationNode> TYPE_ANNOTATION_COMPARATOR = Comparator.<TypeAnnotationNode, String>comparing(ann -> ann.desc)
			.thenComparingInt(ann -> ann.typeRef)
			.thenComparing(ann -> AnnotationUtil.typePathToString(ann.typePath));

	private final AnnotationsEnigmaPlugin plugin;
	private final ProjectView project;
	private final GuiView gui;
	private final ClassNode containingClass;
	private final Object declaration;
	private final EntryView editingEntry;
	private final BaseAnnotationData data;
	private final JEditorPane editor;
	private final JLayeredPane layeredPane;
	private final JScrollPane scrollPane;

	private AnnotationsEditor(GuiView gui, AnnotationsEnigmaPlugin plugin, ProjectView project, EntryView editingEntry, ClassNode containingClass, Object declaration) {
		super(gui.getFrame(), I18n.translate("annotations.edit"), true);

		this.plugin = plugin;
		this.project = project;
		this.gui = gui;
		this.containingClass = containingClass;
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

		ClassNode containingClass = switch (editingEntry) {
		case ClassEntryView classEntry -> project.getBytecode(classEntry.getFullName());
		case FieldEntryView fieldEntry -> project.getBytecode(fieldEntry.getParent().getFullName());
		case MethodEntryView methodEntry -> project.getBytecode(methodEntry.getParent().getFullName());
		default -> throw new IllegalArgumentException("Unsupported entry type: " + editingEntry.getClass().getName());
		};

		if (containingClass == null) {
			return;
		}

		Object declaration = getDeclaration(project, containingClass, editingEntry);

		if (declaration == null) {
			return;
		}

		AnnotationsEditor editor = new AnnotationsEditor(gui, plugin, project, editingEntry, containingClass, declaration);
		editor.setVisible(true);
	}

	public BaseAnnotationData getData() {
		return data;
	}

	public ClassNode getContainingClass() {
		return containingClass;
	}

	public Object getDeclaration() {
		return declaration;
	}

	public EntryView getEditingEntry() {
		return editingEntry;
	}

	@Nullable
	private static Object getDeclaration(ProjectView project, ClassNode containingClass, EntryView entry) {
		return switch (entry) {
		case ClassEntryView ignored -> containingClass;
		case FieldEntryView fieldEntry -> containingClass.fields.stream()
				.filter(field -> field.name.equals(fieldEntry.getName()) && field.desc.equals(fieldEntry.getDescriptor()))
				.findFirst()
				.orElse(null);
		case MethodEntryView methodEntry -> {
			// Sometimes these methods have the name of the bridge but the descriptor of the specialized method.
			// Find the specialized method
			for (MethodNode method : containingClass.methods) {
				MethodEntryView bridgeMethod = project.getJarIndex().getBridgeMethodIndex().getBridgeFromSpecialized(MethodEntryView.create(containingClass.name, method.name, method.desc));
				String methodName = bridgeMethod == null ? method.name : bridgeMethod.getName();

				if (methodName.equals(methodEntry.getName()) && method.desc.equals(methodEntry.getDescriptor())) {
					yield method;
				}
			}

			yield null;
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
		if (!data.annotationsToRemove().isEmpty() || !data.annotationsToAdd().isEmpty()) {
			return false;
		}

		if (!data.typeAnnotationsToRemove().isEmpty() || !data.typeAnnotationsToAdd().isEmpty()) {
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
					addSorted(plugin.data.classes(), fieldEntry.getParent().getFullName(), classData);
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
					addSorted(plugin.data.classes(), methodEntry.getParent().getFullName(), classData);
				}

				boolean res = !data.equals(classData.getMethodData(methodEntry.getName(), methodEntry.getDescriptor()));

				if (res) {
					((MethodAnnotationData) data).parameters().values().removeIf(AnnotationsEditor::isEmpty);
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

		TextWithButtons textWithButtons = new DeclarationGenerator(project, this).generate();

		textWithButtons.buttons().forEach((index, buttons) -> buttons.forEach(button -> layeredPane.add(button, JLayeredPane.PALETTE_LAYER)));

		int offset = 0;

		editor.setText(textWithButtons.text().toString());
		FontMetrics metrics = editor.getFontMetrics(editor.getFont());

		for (Map.Entry<Integer, List<JButton>> buttonEntry : textWithButtons.buttons().entrySet()) {
			int index = buttonEntry.getKey() + offset;
			List<JButton> buttons = buttonEntry.getValue();
			Rectangle2D buttonsLocation;

			try {
				buttonsLocation = editor.modelToView2D(index);
			} catch (BadLocationException e) {
				throw new IllegalStateException(e);
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
				throw new IllegalStateException(e);
			}

			offset += spaceCount;
		}

		Dimension preferredSize = editor.getPreferredSize();
		layeredPane.setSize(Math.max(preferredSize.width, scrollPane.getViewport().getWidth()), Math.max(preferredSize.height, scrollPane.getViewport().getHeight()));
		editor.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
		editor.setCaretPosition(0);
	}

	public JButton createExistingAnnotationButton(List<AnnotationNode> annotations) {
		return createExistingAnnotationButton((create, isTypeAnnotation) -> this.data, annotations);
	}

	public JButton createExistingAnnotationButton(AnnotationDataSupplier dataSupplier, List<AnnotationNode> annotations) {
		String annotationDesc = annotations.getFirst().desc;
		String annotationName = project.deobfuscate(ClassEntryView.create(annotationDesc.substring(1, annotationDesc.length() - 1))).getFullName();
		String annotationStr = new AnnotationStringifier().deobfuscateWith(project).shortenClassReferences().stringify(annotations.getFirst());
		StrikeableButton button = new StrikeableButton(annotationStr);

		boolean[] isRemoved = new boolean[1];

		if (annotations.getFirst() instanceof TypeAnnotationNode typeAnnotation) {
			BaseAnnotationData data = dataSupplier.get(false, true);
			isRemoved[0] = data != null && data.typeAnnotationsToRemove().contains(new TypeAnnotationKey(typeAnnotation.typeRef, AnnotationUtil.typePathToString(typeAnnotation.typePath), annotationName));
		} else {
			BaseAnnotationData data = dataSupplier.get(false, false);
			isRemoved[0] = data != null && data.annotationsToRemove().contains(annotationName);
		}

		button.setStrikethrough(isRemoved[0]);
		button.addActionListener(e -> {
			for (AnnotationNode annotation : annotations) {
				if (annotation instanceof TypeAnnotationNode typeAnnotation) {
					TypeAnnotationKey key = new TypeAnnotationKey(typeAnnotation.typeRef, AnnotationUtil.typePathToString(typeAnnotation.typePath), annotationName);

					if (isRemoved[0]) {
						dataSupplier.get(true, true).typeAnnotationsToRemove().remove(key);
					} else {
						addSorted(dataSupplier.get(true, true).typeAnnotationsToRemove(), key, TYPE_ANNOTATION_KEY_COMPARATOR);
					}
				} else {
					if (isRemoved[0]) {
						dataSupplier.get(true, false).annotationsToRemove().remove(annotationName);
					} else {
						addSorted(dataSupplier.get(true, false).annotationsToRemove(), annotationName);
					}
				}
			}

			isRemoved[0] = !isRemoved[0];
			button.setStrikethrough(isRemoved[0]);
		});

		return button;
	}

	public JButton createAddedAnnotationButton(List<AnnotationNode> annotations, Function<String, List<AnnotationNode>> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		return createAddedAnnotationButton((create, isTypeAnnotation) -> this.data, annotations, annotationCreator, isAnnotationAllowed);
	}

	public JButton createAddedAnnotationButton(AnnotationDataSupplier dataSupplier, List<AnnotationNode> annotations, Function<String, List<AnnotationNode>> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		String annotationStr = new AnnotationStringifier().shortenClassReferences().stringify(annotations.getFirst());
		StrikeableButton button = new StrikeableButton("*" + annotationStr);
		button.addActionListener(e -> {
			AnnotationNode templateAnnotation = SingleAnnotationEditor.show(this, plugin, gui, annotationCreator.andThen(List::getFirst), isAnnotationAllowed, annotations.getFirst());

			if (templateAnnotation == null) {
				for (AnnotationNode annotation : annotations) {
					if (annotation instanceof TypeAnnotationNode) {
						BaseAnnotationData data = dataSupplier.get(false, true);

						if (data != null) {
							data.typeAnnotationsToAdd().remove(annotation);
						}
					} else {
						BaseAnnotationData data = dataSupplier.get(false, false);

						if (data != null) {
							data.annotationsToAdd().remove(annotation);
						}
					}
				}
			} else {
				List<AnnotationNode> newAnnotations = annotationCreator.apply(templateAnnotation.desc);

				for (AnnotationNode newAnnotation : newAnnotations) {
					templateAnnotation.accept(newAnnotation);

					if (newAnnotation instanceof TypeAnnotationNode newTypeAnnotation) {
						BaseAnnotationData data = dataSupplier.get(true, true);

						if (data.typeAnnotationsToAdd().contains(newTypeAnnotation)) {
							return;
						}

						// replaces the annotation
						addSorted(data.typeAnnotationsToAdd(), newTypeAnnotation, TYPE_ANNOTATION_COMPARATOR);
					} else {
						BaseAnnotationData data = dataSupplier.get(true, false);

						if (data.annotationsToAdd().contains(newAnnotation)) {
							return;
						}

						// replaces the annotation
						addSorted(data.annotationsToAdd(), newAnnotation, ANNOTATION_COMPARATOR);
					}
				}
			}

			refreshUi();
		});
		return button;
	}

	public JButton createPlusButton(Function<String, List<AnnotationNode>> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		return createPlusButton((create, isTypeAnnotation) -> this.data, annotationCreator, isAnnotationAllowed);
	}

	public JButton createPlusButton(AnnotationDataSupplier dataSupplier, Function<String, List<AnnotationNode>> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		JButton button = new JButton("+");
		button.addActionListener(e -> {
			AnnotationNode templateAnnotation = SingleAnnotationEditor.show(this, plugin, gui, annotationCreator.andThen(List::getFirst), isAnnotationAllowed, null);

			if (templateAnnotation == null) {
				return;
			}

			List<AnnotationNode> newAnnotations = annotationCreator.apply(templateAnnotation.desc);

			for (AnnotationNode newAnnotation : newAnnotations) {
				templateAnnotation.accept(newAnnotation);

				if (newAnnotation instanceof TypeAnnotationNode newTypeAnnotation) {
					addSorted(dataSupplier.get(true, true).typeAnnotationsToAdd(), newTypeAnnotation, TYPE_ANNOTATION_COMPARATOR);
				} else {
					addSorted(dataSupplier.get(true, false).annotationsToAdd(), newAnnotation, ANNOTATION_COMPARATOR);
				}
			}

			refreshUi();
		});
		return button;
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

	private static <E> void addSorted(List<E> list, E value, Comparator<E> comp) {
		list.sort(comp);
		int index = Collections.binarySearch(list, value, comp);

		if (index < 0) {
			list.add(-index - 1, value);
		} else {
			list.set(index, value);
		}
	}

	private static <K extends Comparable<K>, V> void addSorted(Map<K, V> map, K key, V value) {
		List<Pair<K, V>> entries = new ArrayList<>(map.size() + 1);
		map.forEach((k, v) -> entries.add(new Pair<>(k, v)));
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

	@FunctionalInterface
	public interface AnnotationDataSupplier {
		@Nullable
		@Contract("true, _ -> !null")
		BaseAnnotationData get(boolean create, boolean isTypeAnnotation);
	}
}
