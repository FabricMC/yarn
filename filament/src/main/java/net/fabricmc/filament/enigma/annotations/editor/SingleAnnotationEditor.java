package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.ListSelectionModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import cuchaz.enigma.api.I18n;
import cuchaz.enigma.api.view.GuiView;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.filament.enigma.annotations.AnnotationsEnigmaPlugin;

public class SingleAnnotationEditor extends JDialog {
	private final AnnotationsEnigmaPlugin plugin;
	private final Function<String, AnnotationNode> annotationCreator;
	private final Predicate<ClassNode> isAnnotationAllowed;
	@Nullable
	private AnnotationNode annotation;
	private boolean hasResult = false;
	private final JEditorPane editor;

	private List<AnnotationParser.ParseError> errors = List.of();
	private final JToolTip errorToolTip;
	@Nullable
	private Popup errorPopup;
	@Nullable
	private AnnotationParser.ParseError hoveredError;

	private final JList<CompletionOption> completionsList;
	private final DefaultListModel<CompletionOption> completionsModel;
	private final JScrollPane completionsScroll;
	private final JPopupMenu completionsPopup;
	private boolean isInsertingCompletion = false;

	private final AWTEventListener globalListener;

	public SingleAnnotationEditor(
			JDialog owner,
			AnnotationsEnigmaPlugin plugin,
			GuiView gui,
			Function<String, AnnotationNode> annotationCreator,
			Predicate<ClassNode> isAnnotationAllowed,
			@Nullable AnnotationNode original
	) {
		super(owner, I18n.translate("annotations.edit.single"), true);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setSize((int) (600 * gui.getScale()), (int) (300 * gui.getScale()));
		setLocationRelativeTo(owner);

		this.plugin = plugin;
		this.annotationCreator = annotationCreator;
		this.isAnnotationAllowed = isAnnotationAllowed;
		this.annotation = original;
		this.editor = gui.createEditorPane();

		if (original != null) {
			this.editor.setText(new AnnotationStringifier().stringify(original));
		}

		this.editor.setEditable(true);
		this.errorToolTip = this.editor.createToolTip();

		this.completionsModel = new DefaultListModel<>();
		this.completionsList = new JList<>(completionsModel);
		this.completionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.completionsList.setCellRenderer(new CompletionRenderer(editor));
		this.completionsList.setFocusable(false); // keep focus in editor
		this.completionsList.setRequestFocusEnabled(false);
		this.completionsScroll = new JScrollPane(completionsList);
		this.completionsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		this.completionsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		this.completionsPopup = new JPopupMenu();
		this.completionsPopup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
		this.completionsPopup.setLayout(new BorderLayout());
		this.completionsPopup.add(completionsScroll, BorderLayout.CENTER);

		this.editor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onTextChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onTextChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onTextChanged();
			}
		});

		getContentPane().setLayout(new BorderLayout());

		getContentPane().add(new JScrollPane(editor), BorderLayout.CENTER);

		JPanel buttonsPanel = new JPanel();
		getContentPane().add(buttonsPanel, BorderLayout.SOUTH);

		JButton okButton = new JButton(I18n.translate("prompt.ok"));
		buttonsPanel.add(okButton);
		okButton.addActionListener(event -> {
			if (errors.isEmpty()) {
				hasResult = true;
				dispose();
			} else {
				JOptionPane.showMessageDialog(this, I18n.translate("annotations.edit.errors"), I18n.translate("annotations.edit.single"), JOptionPane.ERROR_MESSAGE);
			}
		});

		JButton cancelButton = new JButton(I18n.translate("prompt.cancel"));
		buttonsPanel.add(cancelButton);
		cancelButton.addActionListener(event -> {
			annotation = original;
			hasResult = true;
			dispose();
		});

		if (original != null) {
			JButton deleteButton = new JButton(I18n.translate("annotations.edit.delete"));
			buttonsPanel.add(deleteButton);
			deleteButton.addActionListener(event -> {
				annotation = null;
				hasResult = true;
				dispose();
			});
		}

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (!hasResult) {
					annotation = original;
					hasResult = true;
					dispose();
				}
			}
		});

		addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowLostFocus(WindowEvent e) {
				hideErrorPopup();
				stopAutoCompletion();
			}
		});

		MouseListener mouseExitListener = new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				if (errorPopup != null) {
					Point editorPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), editor);
					boolean inEditor = editor.contains(editorPoint);
					boolean inToolTip = errorToolTip.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), errorToolTip));

					if (!inToolTip && (!inEditor || getErrorAt(editorPoint) != hoveredError)) {
						hideErrorPopup();
					}
				}
			}
		};

		editor.addMouseListener(mouseExitListener);
		errorToolTip.addMouseListener(mouseExitListener);

		editor.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				AnnotationParser.ParseError popupError = getErrorAt(e.getPoint());

				if (popupError != hoveredError) {
					hoveredError = popupError;

					if (errorPopup != null) {
						errorPopup.hide();
						errorPopup = null;
					}

					if (popupError != null) {
						errorToolTip.setTipText(popupError.message());
						errorPopup = PopupFactory.getSharedInstance().getPopup(editor, errorToolTip, e.getXOnScreen() + 2, e.getYOnScreen() + 2);
						errorPopup.show();
					}
				}
			}
		});

		editor.addCaretListener(e -> {
			// only update completions popup if it's already visible
			if (completionsPopup.isVisible()) {
				AnnotationParser parser = new AnnotationParser(plugin, editor.getCaretPosition());
				parser.parse(editor.getText(), annotationCreator, isAnnotationAllowed);
				startAutoCompletion(parser.getCompletions());
			}
		});

		editor.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (completionsPopup.isVisible()) {
					switch (e.getKeyCode()) {
						case KeyEvent.VK_DOWN -> {
							completionsList.setSelectedIndex((completionsList.getSelectedIndex() + 1) % completionsModel.getSize());
							e.consume();
						}
						case KeyEvent.VK_UP -> {
							completionsList.setSelectedIndex(Math.floorMod((completionsList.getSelectedIndex() - 1), completionsModel.getSize()));
							e.consume();
						}
						case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
							acceptAutoCompletion();
							e.consume();
						}
						case KeyEvent.VK_ESCAPE -> {
							stopAutoCompletion();
							e.consume();
						}
					}
				}
			}
		});

		globalListener = e -> {
			if (e instanceof MouseEvent me) {
				Point mousePos = me.getPoint();

				if (completionsPopup.isVisible()) {
					Point pointInCompletionPopup = SwingUtilities.convertPoint(null, mousePos, completionsPopup);

					if (completionsPopup.contains(pointInCompletionPopup)) {
						Point pointInCompletionList = SwingUtilities.convertPoint(null, mousePos, completionsList);
						int index = completionsList.locationToIndex(pointInCompletionList);

						if (me.getID() == MouseEvent.MOUSE_PRESSED) {
							if (index >= 0) {
								completionsList.setSelectedIndex(index);
								acceptAutoCompletion();
							}
						} else if (me.getID() == MouseEvent.MOUSE_MOVED) {
							if (index >= 0) {
								completionsList.setSelectedIndex(index);
							}
						}
					} else {
						if (me.getID() == MouseEvent.MOUSE_PRESSED) {
							stopAutoCompletion();
						}
					}
				}
			}
		};

		Toolkit.getDefaultToolkit().addAWTEventListener(globalListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
	}

	@Nullable
	public static AnnotationNode show(
			JDialog owner,
			AnnotationsEnigmaPlugin plugin,
			GuiView gui,
			Function<String, AnnotationNode> annotationCreator,
			Predicate<ClassNode> isAnnotationAllowed,
			@Nullable AnnotationNode original
	) {
		SingleAnnotationEditor editor = new SingleAnnotationEditor(owner, plugin, gui, annotationCreator, isAnnotationAllowed, original);
		editor.setVisible(true);
		Toolkit.getDefaultToolkit().removeAWTEventListener(editor.globalListener);
		return editor.annotation;
	}

	private void hideErrorPopup() {
		if (errorPopup != null) {
			errorPopup.hide();
			errorPopup = null;
			hoveredError = null;
		}
	}

	@Nullable
	private AnnotationParser.ParseError getErrorAt(Point point) {
		int offset = editor.viewToModel2D(point);

		for (AnnotationParser.ParseError error : errors) {
			if (offset >= error.startInclusive() && offset < error.endExclusive() || (offset == error.startInclusive() && offset == error.endExclusive())) {
				return error;
			}
		}

		return null;
	}

	private void onTextChanged() {
		boolean isInsertingCompletion = this.isInsertingCompletion;

		// invokeLater because the caret is moved after the text is changed
		SwingUtilities.invokeLater(() -> {
			int caretPosition = Math.min(editor.getCaretPosition(), editor.getDocument().getLength());

			hideErrorPopup();
			editor.getHighlighter().removeAllHighlights();
			AnnotationParser parser = new AnnotationParser(plugin, caretPosition);
			AnnotationNode parsedAnnotation = parser.parse(editor.getText(), annotationCreator, isAnnotationAllowed);
			errors = parser.getErrors();

			// even if inserting completion, keep going if we're not on an identifier
			if (!isInsertingCompletion || caretPosition == getPrefixStart()) {
				startAutoCompletion(parser.getCompletions());
			}

			if (errors.isEmpty()) {
				annotation = parsedAnnotation;
			} else {
				for (AnnotationParser.ParseError error : errors) {
					try {
						editor.getHighlighter().addHighlight(error.startInclusive(), error.endExclusive(), ErrorHighlightPainter.INSTANCE);
					} catch (BadLocationException e) {
						throw new IllegalStateException(e);
					}
				}
			}
		});
	}

	private void startAutoCompletion(List<CompletionOption> completions) {
		int caretPosition = Math.min(editor.getCaretPosition(), editor.getDocument().getLength());
		int prefixStart = getPrefixStart();
		String prefix;

		try {
			prefix = editor.getText(prefixStart, caretPosition - prefixStart);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}

		completions = completions.stream()
				.filter(completion -> completion.prefix().startsWith(prefix))
				.toList();

		if (completions.isEmpty()) {
			stopAutoCompletion();
			return;
		}

		completionsModel.clear();
		completionsModel.addAll(completions);
		completionsList.setSelectedIndex(0);

		Rectangle2D caretLocation;

		try {
			caretLocation = editor.modelToView2D(caretPosition);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}

		completionsScroll.setPreferredSize(new Dimension(completionsPopup.getPreferredSize().width, Math.min(8, completions.size()) * editor.getFontMetrics(editor.getFont()).getHeight()));
		completionsPopup.pack();

		completionsPopup.show(editor, (int) caretLocation.getX() + 2, (int) (caretLocation.getY() + caretLocation.getHeight()) + 2);

		editor.requestFocusInWindow();
	}

	private void stopAutoCompletion() {
		completionsPopup.setVisible(false);
	}

	private void acceptAutoCompletion() {
		CompletionOption completion = completionsList.getSelectedValue();
		stopAutoCompletion();

		if (completion == null) {
			return;
		}

		int prefixStart = getPrefixStart();

		isInsertingCompletion = true;

		try {
			editor.getDocument().remove(prefixStart, editor.getCaretPosition() - prefixStart);
			editor.getDocument().insertString(prefixStart, completion.completion(), null);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}

		editor.setCaretPosition(prefixStart + completion.completion().length());
		isInsertingCompletion = false;
	}

	private int getPrefixStart() {
		int prefixStart = Math.min(editor.getCaretPosition(), editor.getDocument().getLength()) - 1;

		try {
			while (prefixStart >= 0 && Character.isJavaIdentifierPart(editor.getText(prefixStart, 1).charAt(0))) {
				prefixStart--;
			}
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}

		return prefixStart + 1;
	}
}
