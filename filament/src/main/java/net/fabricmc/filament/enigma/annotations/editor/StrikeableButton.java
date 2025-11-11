package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JButton;

public class StrikeableButton extends JButton {
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
