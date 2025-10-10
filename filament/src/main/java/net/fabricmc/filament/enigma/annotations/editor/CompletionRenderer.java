package net.fabricmc.filament.enigma.annotations.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

public class CompletionRenderer implements ListCellRenderer<CompletionOption> {
	private final JEditorPane editor;
	private final Color selBg = UIManager.getColor("List.selectionBackground");
	private final Color selFg = UIManager.getColor("List.selectionForeground");
	private final Color normalBg = UIManager.getColor("List.background");
	private final Color normalFg = UIManager.getColor("List.foreground");

	public CompletionRenderer(JEditorPane editor) {
		this.editor = editor;
	}

	@Override
	public Component getListCellRendererComponent(JList<? extends CompletionOption> list, CompletionOption value, int index, boolean isSelected, boolean cellHasFocus) {
		Color selBg = this.selBg != null ? this.selBg : list.getSelectionBackground();
		Color selFg = this.selFg != null ? this.selFg : list.getSelectionForeground();
		Color normalBg = this.normalBg != null ? this.normalBg : list.getBackground();
		Color normalFg = this.normalFg != null ? this.normalFg : list.getForeground();

		JPanel row = new JPanel();
		row.setLayout(new BorderLayout());
		row.setOpaque(true);
		row.setBackground(isSelected ? selBg : normalBg);

		JLabel prefixLabel = new JLabel(value.prefix());
		prefixLabel.setFont(editor.getFont());
		prefixLabel.setForeground(isSelected ? selFg : normalFg);
		row.add(prefixLabel, BorderLayout.WEST);

		if (value.context() != null) {
			JLabel contextLabel = new JLabel(value.context());
			contextLabel.setFont(editor.getFont());
			contextLabel.setForeground(Color.GRAY);
			row.add(contextLabel, BorderLayout.EAST);
		}

		return row;
	}
}
