package net.fabricmc.filament.task.minecraft;

import java.io.IOException;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.stitch.merge.JarMerger;

public abstract class MergeMinecraftTask extends FileOutputTask {
	@InputFile
	public abstract RegularFileProperty getClientJar();

	@InputFile
	public abstract RegularFileProperty getServerJar();

	@TaskAction
	public void run() throws IOException {
		try (JarMerger jarMerger = new JarMerger(
				getClientJar().getAsFile().get(),
				getServerJar().getAsFile().get(),
				getOutputFile().getAsFile().get())) {
			jarMerger.merge();
		}
	}
}
