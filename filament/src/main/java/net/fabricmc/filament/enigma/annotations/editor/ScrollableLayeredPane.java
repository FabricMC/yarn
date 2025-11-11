package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JEditorPane;
import javax.swing.JLayeredPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TextUI;

public class ScrollableLayeredPane extends JLayeredPane implements Scrollable {
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
