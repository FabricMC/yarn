/*
 * This file is free for everyone to use under the Creative Commons Zero license.
 */

package net.fabricmc.yarn.constants;

/**
 * Constants of Mining Levels.
 * <p>Mining levels are used by blocks to determine the strength of the tools required to successfully harvest them.
 * <br>All tool materials have an assigned mining level. If a tool's mining level is equal to or greater than the block's,
 * the tool will apply its efficiency bonus and the block will drop its loot table.
 * <p>Blocks without mining levels, or items that aren't tools, use {@link MiningLevels#HAND}.
 */
public final class MiningLevels {
	/**
	 * Blocks with this level do not require a tool to harvest.
	 * <br>This is the default level for blocks and items.
	 */
	public static final int HAND = -1;

	/**
	 * Blocks with this level require a Wooden tool or better to harvest.
	 * <br>In addition to Wooden Tools, Golden Tools also use this level.
	 */
	public static final int WOOD = 0;

	/**
	 * Blocks with this level require a Stone tool or better to harvest.
	 */
	public static final int STONE = 1;

	/**
	 * Blocks with this level require an Iron tool or better to harvest.
	 */
	public static final int IRON = 2;

	/**
	 * Blocks with this level require a Diamond tool or better to harvest.
	 */
	public static final int DIAMOND = 3;

	/**
	 * Blocks with this level require a Netherite tool or better to harvest.
	 */
	public static final int NETHERITE = 4;

	private MiningLevels() {
	}
}
