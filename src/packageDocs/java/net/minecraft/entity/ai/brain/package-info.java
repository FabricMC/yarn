/*
 * This file is free for everyone to use under the Creative Commons Zero license.
 */

/**
 * This and its subpackages make up the brain system used by some modern Minecraft entities,
 * such as villagers, piglins, and axolotls.
 *
 * <p>Compared to {@linkplain net.minecraft.entity.ai.goal the goal system}, the brain system's
 * main advantage is that it allows sharing of certain expensive calculation results in the
 * form of memory by the individual tasks.
 */

package net.minecraft.entity.ai.brain;
