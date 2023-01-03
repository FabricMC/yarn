package net.fabricmc.filament.task.minecraft;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.FilamentGradlePlugin;
import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.download.Download;

public abstract class MinecraftVersionMetaTask extends FilamentTask {
	@Input
	public abstract Property<String> getMinecraftVersion();

	@Input
	public abstract Property<String> getMinecraftVersionManifestUrl();

	@OutputFile
	public abstract RegularFileProperty getVersionManifest();

	@OutputFile
	public abstract RegularFileProperty getVersionMetadata();

	public MinecraftVersionMetaTask() {
		// Use the Minecraft version as an input to ensure the task re-runs on upgrade
		getMinecraftVersion().set(getExtension().getMinecraftVersion());
		getMinecraftVersionManifestUrl().set(getExtension().getMinecraftVersionManifestUrl());

		getVersionManifest().set(new File(getExtension().getCacheDirectory(), "version_manifest.json"));
		getVersionMetadata().set(new File(getExtension().getMinecraftDirectory(), "version.json"));
	}

	@TaskAction
	public void run() throws IOException, URISyntaxException {
		final Path versionManifestPath = getVersionManifest().getAsFile().get().toPath();
		final Path versionMetadataPath = getVersionMetadata().getAsFile().get().toPath();

		final String versionManifest = Download.create(getMinecraftVersionManifestUrl().get())
				.downloadString(versionManifestPath);

		final ManifestVersion mcManifest = FilamentGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, ManifestVersion.class);

		ManifestVersion.Versions version = mcManifest.versions().stream()
				.filter(versions -> versions.id.equalsIgnoreCase(getMinecraftVersion().get()))
				.findFirst()
				.orElse(null);

		if (version == null) {
			throw new RuntimeException("Failed to find minecraft version: " + getMinecraftVersion().get());
		}

		Download.create(version.url)
				.sha1(version.sha1)
				.downloadPath(versionMetadataPath);
	}

	public static Provider<MinecraftVersionMeta> readMetadata(Provider<MinecraftVersionMetaTask> task) {
		return task.flatMap(t -> readMetadata(t.getVersionMetadata()));
	}

	public static Provider<MinecraftVersionMeta> readMetadata(RegularFileProperty file) {
		return file.map(regularFile -> {
			try {
				String versionManifest = Files.readString(regularFile.getAsFile().toPath(), StandardCharsets.UTF_8);
				return FilamentGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, MinecraftVersionMeta.class);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
