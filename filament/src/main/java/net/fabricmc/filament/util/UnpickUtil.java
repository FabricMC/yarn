package net.fabricmc.filament.util;

import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Writer;

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
	public static String getLfOutput(UnpickV2Writer writer) {
		return writer.getOutput().replace(System.lineSeparator(), "\n");
	}
}
