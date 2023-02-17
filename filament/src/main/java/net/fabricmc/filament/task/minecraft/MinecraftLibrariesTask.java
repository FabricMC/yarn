package net.fabricmc.filament.task.minecraft;

import javax.inject.Inject;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFiles;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public abstract class MinecraftLibrariesTask extends FilamentTask {
	@InputFile
	public abstract RegularFileProperty getMetadataFile();

	@OutputFiles
	public abstract ConfigurableFileCollection getFiles();

	@Inject
	public abstract DependencyHandler getDependencyHandler();

	@Inject
	public abstract ConfigurationContainer getConfigurationContainer();

	public MinecraftLibrariesTask() {
		getFiles().from(
				MinecraftVersionMetaTask.readMetadata(getMetadataFile())
						.map(this::getDependencies)
						.map(dependencies -> getConfigurationContainer().detachedConfiguration(dependencies))
						.map(Configuration::resolve)
		);
	}

	private Dependency[] getDependencies(MinecraftVersionMeta meta) {
		return meta.libraries().stream()
				.filter(library -> library.artifact() != null)
				.map(library -> getDependencyHandler().create(library.name()))
				.toArray(Dependency[]::new);
	}
}
