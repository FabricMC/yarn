/*
 * This file is free for everyone to use under the Creative Commons Zero license.
 */

/**
 * The base package for all Minecraft classes. All Minecraft classes belong to this package
 * in their intermediary names.
 *
 * <p>Unmapped classes go into this package by default. This package additionally contains
 * {@link Bootstrap}, {@link SharedConstants}, and {@link MinecraftVersion} classes.
 *
 * <p>While it's known that some obfuscated Minecraft classes are under other packages like
 * {@code com.mojang.*}, yarn keeps all mapped classes under {@code net.minecraft.*} since
 * there is no convincing evidence those classes are independent from Minecraft.
 */

package net.minecraft;
