package net.fabricmc.filament.task.minecraft;

import java.io.IOException;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger;

public abstract class MergeMinecraftTask extends FileOutputTask {
	@InputFile
	public abstract RegularFileProperty getClientJar();

	@InputFile
	public abstract RegularFileProperty getServerJar();

	@TaskAction
	public void run() throws IOException {
		try (MinecraftJarMerger jarMerger = new MinecraftJarMerger(
				getClientJar().getAsFile().get(),
				getServerJar().getAsFile().get(),
				getOutput().getAsFile().get())) {
			jarMerger.merge();
		}
	}
}
