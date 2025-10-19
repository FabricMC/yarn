package net.fabricmc.filament.enigma.annotations;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.TypePath;

public final class AnnotationUtil {
	private AnnotationUtil() {
	}

	public static String getSimpleName(String internalName) {
		int slashIndex = internalName.lastIndexOf('/');
		String simpleName = internalName.substring(slashIndex + 1);
		return simpleName.replace('$', '.');
	}

	public static String typePathToString(@Nullable TypePath typePath) {
		return typePath == null ? "" : typePath.toString();
	}
}
