package net.fabricmc.filament.enigma.annotations;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.AnnotationNode;

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

	public static boolean areEqual(AnnotationNode a, AnnotationNode b) {
		if (!a.desc.equals(b.desc)) {
			return false;
		}

		List<Object> valuesA = Objects.requireNonNullElse(a.values, List.of());
		List<Object> valuesB = Objects.requireNonNullElse(b.values, List.of());

		if (valuesA.size() != valuesB.size()) {
			return false;
		}

		for (int i = 0; i < valuesA.size(); i += 2) {
			if (!valuesA.get(i).equals(valuesB.get(i))) {
				return false;
			}

			if (!areValuesEqual(valuesA.get(i + 1), valuesB.get(i + 1))) {
				return false;
			}
		}

		return true;
	}

	private static boolean areValuesEqual(Object a, Object b) {
		if (a instanceof List<?> listA) {
			if (!(b instanceof List<?> listB)) {
				return false;
			}

			if (listA.size() != listB.size()) {
				return false;
			}

			for (int i = 0; i < listA.size(); i++) {
				if (!areValuesEqual(listA.get(i), listB.get(i))) {
					return false;
				}
			}

			return true;
		}

		if (a instanceof AnnotationNode annA) {
			if (!(b instanceof AnnotationNode annB)) {
				return false;
			}

			return areEqual(annA, annB);
		}

		if (a instanceof String[] enumValuesA) {
			if (!(b instanceof String[] enumValuesB)) {
				return false;
			}

			return Arrays.equals(enumValuesA, enumValuesB);
		}

		return a.equals(b);
	}
}
