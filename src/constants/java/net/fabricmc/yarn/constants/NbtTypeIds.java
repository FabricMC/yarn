/*
 * This file is free for everyone to use under the Creative Commons Zero license.
 */

package net.fabricmc.yarn.constants;

/**
 * Constants representing the type of an {@linkplain net.minecraft.nbt.NbtElement NbtElement}.
 *
 * @see net.minecraft.nbt.NbtElement#getType()
 */
public final class NbtTypeIds {
	/**
	 * An NBT null value.
	 *
	 * @see net.minecraft.nbt.NbtNull
	 */
	public static final int NULL = 0;

	/**
	 * An NBT byte value.
	 *
	 * @see net.minecraft.nbt.NbtByte
	 */
	public static final int BYTE = 1;

	/**
	 * An NBT short value.
	 *
	 * @see net.minecraft.nbt.NbtShort
	 */
	public static final int SHORT = 2;

	/**
	 * An NBT integer value.
	 *
	 * @see net.minecraft.nbt.NbtInt
	 */
	public static final int INT = 3;

	/**
	 * An NBT long value.
	 *
	 * @see net.minecraft.nbt.NbtLong
	 */
	public static final int LONG = 4;

	/**
	 * An NBT float value.
	 *
	 * @see net.minecraft.nbt.NbtFloat
	 */
	public static final int FLOAT = 5;

	/**
	 * An NBT double value.
	 *
	 * @see net.minecraft.nbt.NbtDouble
	 */
	public static final int DOUBLE = 6;

	/**
	 * An NBT byte array value.
	 *
	 * @see net.minecraft.nbt.NbtByteArray
	 */
	public static final int BYTE_ARRAY = 7;

	/**
	 * An NBT string value.
	 *
	 * @see net.minecraft.nbt.NbtString
	 */
	public static final int STRING = 8;

	/**
	 * An NBT list value.
	 *
	 * @see net.minecraft.nbt.NbtList
	 */
	public static final int LIST = 9;

	/**
	 * An NBT compound value.
	 *
	 * @see net.minecraft.nbt.NbtCompound
	 */
	public static final int COMPOUND = 10;

	/**
	 * An NBT integer array value.
	 *
	 * @see net.minecraft.nbt.NbtIntArray
	 */
	public static final int INT_ARRAY = 11;

	/**
	 * An NBT long array value.
	 *
	 * @see net.minecraft.nbt.NbtLongArray
	 */
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
