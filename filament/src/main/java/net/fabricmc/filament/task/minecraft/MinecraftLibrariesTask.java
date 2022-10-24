package net.fabricmc.filament.task.minecraft;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFiles;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public abstract class MinecraftLibrariesTask extends FilamentTask {
	@OutputFiles
	public abstract ConfigurableFileCollection getFiles();

	public static Dependency[] getDependencies(Project project, MinecraftVersionMeta meta) {
		return meta.libraries().stream()
				.filter(library -> library.artifact() != null)
				.map(library -> project.getDependencies().create(library.name()))
				.toArray(Dependency[]::new);
	}
}
