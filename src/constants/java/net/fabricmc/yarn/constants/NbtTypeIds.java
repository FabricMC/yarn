package net.fabricmc.yarn.constants;

/**
 * Constants representing the type of an {@linkplain net.minecraft.nbt.NbtElement NbtElement}.
 *
 * @see net.minecraft.nbt.NbtElement#getType()
 */
public final class NbtTypeIds {
	public static final int NULL = 0;
	public static final int BYTE = 1;
	public static final int SHORT = 2;
	public static final int INT = 3;
	public static final int LONG = 4;
	public static final int FLOAT = 5;
	public static final int DOUBLE = 6;
	public static final int BYTE_ARRAY = 7;
	public static final int STRING = 8;
	public static final int LIST = 9;
	public static final int COMPOUND = 10;
	public static final int INT_ARRAY = 11;
	public static final int LONG_ARRAY = 12;

	/**
	 * A wildcard value that can be used for <i>testing</i> whether an {@linkplain net.minecraft.nbt.NbtElement NbtElement} is an {@linkplain net.minecraft.nbt.AbstractNbtNumber AbstractNbtNumber}.
	 *
	 * @see net.minecraft.nbt.NbtCompound#getType(String)
	 * @see net.minecraft.nbt.NbtCompound#contains(String, int)
	 */
	public static final int NUMBER = 99;

	private NbtTypeIds() {
	}
}
