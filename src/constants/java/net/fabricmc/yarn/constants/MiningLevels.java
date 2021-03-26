package net.fabricmc.yarn.constants;

public final class MiningLevels {
	/**
	 * Does not require a tool to harvest.
	 */
	public static final int HAND = -1;

	/**
	 * Requires a wooden tool or better to harvest.
	 * Also used by golden tools.
	 */
	public static final int WOOD = 0;

	/**
	 * Requires a stone tool or better to harvest.
	 */
	public static final int STONE = 1;

	/**
	 * Requires an iron tool or better to harvest.
	 */
	public static final int IRON = 2;

	/**
	 * Requires a diamond tool or better to harvest.
	 */
	public static final int DIAMOND = 3;

	/**
	 * Requires a netherite tool or better to harvest.
	 */
	public static final int NETHERITE = 4;

	private MiningLevels() {
	}
}
