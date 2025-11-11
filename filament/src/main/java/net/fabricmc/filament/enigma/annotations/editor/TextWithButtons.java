package net.fabricmc.filament.enigma.annotations.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JButton;

public record TextWithButtons(StringBuilder text, TreeMap<Integer, List<JButton>> buttons) {
	public TextWithButtons() {
		this(new StringBuilder(), new TreeMap<>());
	}

	public void append(String text) {
		this.text.append(text);
	}

	public void append(JButton button) {
		this.buttons.computeIfAbsent(this.text.length(), k -> new ArrayList<>(1)).add(button);
	}

	public void append(TextWithButtons other) {
		other.buttons.forEach((index, buttons) -> {
			this.buttons.computeIfAbsent(this.text.length() + index, k -> new ArrayList<>(buttons.size())).addAll(buttons);
		});

		this.text.append(other.text);
	}
}
