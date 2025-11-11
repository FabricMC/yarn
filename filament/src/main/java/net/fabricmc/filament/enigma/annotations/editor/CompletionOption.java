package net.fabricmc.filament.enigma.annotations.editor;

import org.jetbrains.annotations.Nullable;

public record CompletionOption(
		String prefix,
		String completion,
		@Nullable String context
) {
}
