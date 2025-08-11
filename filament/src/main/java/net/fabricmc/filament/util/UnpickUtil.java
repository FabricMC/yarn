package net.fabricmc.filament.util;

import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Writer;

public final class UnpickUtil {
	private UnpickUtil() {
	}

	/**
	 * Gets the output of the writer with all {@linkplain System#lineSeparator() system line separators}
	 * replaced with {@code \n}.
	 *
	 * @param writer the writer
	 * @return the output using LF as the line separator
	 */
	public static String getLfOutput(UnpickV3Writer writer) {
		return writer.getOutput().replace(System.lineSeparator(), "\n");
	}
}
