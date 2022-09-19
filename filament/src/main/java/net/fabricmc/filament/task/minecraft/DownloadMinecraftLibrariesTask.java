package net.fabricmc.filament.task.minecraft;

import javax.inject.Inject;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public abstract class DownloadMinecraftLibrariesTask extends FilamentTask {
	@Input
	public abstract Property<MinecraftVersionMeta> getVersionMeta();

	@OutputFiles
	public abstract ConfigurableFileCollection getLibraries();

	@Inject
	public DownloadMinecraftLibrariesTask() {
		getVersionMeta().finalizeValueOnRead();
		getLibraries().finalizeValueOnRead();

		Provider<FileCollection> librariesProvider = getVersionMeta()
				.map(meta -> {
					final Dependency[] dependencies = meta.libraries().stream().map(library ->
							                                                                getProject().getDependencies().create(library.name())
					).toArray(Dependency[]::new);

					return getProject().getConfigurations().detachedConfiguration(dependencies).fileCollection();
				});

		getLibraries().from(librariesProvider);
	}
}
