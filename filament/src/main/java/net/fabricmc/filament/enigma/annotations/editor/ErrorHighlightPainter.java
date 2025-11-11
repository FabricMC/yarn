package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

public class ErrorHighlightPainter implements Highlighter.HighlightPainter {
	public static final ErrorHighlightPainter INSTANCE = new ErrorHighlightPainter();

	private ErrorHighlightPainter() {
	}

	@Override
	public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
		Graphics2D g2 = (Graphics2D) g.create();

		try {
			g2.setColor(Color.RED);

			if (p0 == p1) {
				try {
					Rectangle2D r = c.modelToView2D(p0);
					drawWavyLine(g2, r.getX(), r.getX() + 10, r.getY() + r.getHeight() - 2.0);
				} catch (BadLocationException e) {
					// ignore
				}

				return;
			}

			// We'll paint underline per visual row. Use Utilities to find row boundaries
			int offset = p0;

			while (offset < p1) {
				try {
					int rowEnd = Utilities.getRowEnd(c, offset);
					int segEnd = Math.min(p1, rowEnd + 1); // +1 to include last char on the line
					// get bounds for the first and last char of this segment:
					Rectangle2D r0 = c.modelToView2D(offset);
					Rectangle2D r1 = c.modelToView2D(Math.max(offset, segEnd));

					if (r0 == null || r1 == null) {
						// if view not ready, fall back to next
						offset = segEnd;
						continue;
					}

					double x1 = r0.getX();
					double x2 = r1.getX() + r1.getWidth();
					double y = r0.getY() + r0.getHeight() - 2.0; // slightly above bottom

					drawWavyLine(g2, x1, x2, y);

					offset = segEnd;
				} catch (BadLocationException ex) {
					// can't compute this row — bail out
					break;
				}
			}
		} finally {
			g2.dispose();
		}
	}

	private static void drawWavyLine(Graphics2D g2, double x1, double x2, double y) {
		final double WAVE_WIDTH = 2.0; // width of one zigzag
		final double WAVE_HEIGHT = 1.5; // vertical amplitude

		// If the region is too narrow, draw a straight line
		if (x2 - x1 < WAVE_WIDTH * 1.5) {
			g2.draw(new Line2D.Double(x1, y, x2, y));
			return;
		}

		Path2D path = new Path2D.Double();
		path.moveTo(x1, y);
		boolean up = true;
		double x = x1;

		while (x < x2) {
			double nx = Math.min(x + WAVE_WIDTH, x2);
			double midx = (x + nx) / 2.0;
			double ctrlY = up ? (y - WAVE_HEIGHT) : (y + WAVE_HEIGHT);
			// quadratic approximation with a line to the midpoint and to next
			path.quadTo(midx, ctrlY, nx, y);
			up = !up;
			x = nx;
		}

		g2.draw(path);
	}
}
