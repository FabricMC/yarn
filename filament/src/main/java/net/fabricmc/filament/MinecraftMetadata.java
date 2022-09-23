package net.fabricmc.filament;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.download.Download;

public class MinecraftMetadata {
	public static void setup(Project project) throws Exception {
		FilamentExtension extension = FilamentExtension.get(project);

		final String versionManifest = Download.create(extension.getMinecraftVersionManifestUrl().get())
				.defaultCache()
				.downloadString();

		final ManifestVersion mcManifest = FilamentGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, ManifestVersion.class);

		ManifestVersion.Versions version = mcManifest.versions().stream()
				.filter(versions -> versions.id.equalsIgnoreCase(extension.getMinecraftVersion().get()))
				.findFirst()
				.orElse(null);

		if (version == null) {
			throw new RuntimeException("Failed to find minecraft version: " + extension.getMinecraftVersion().get());
		}

		final var outputFile = new File(extension.getCacheDirectory(), "version_manifest.json");

		Download.create(version.url)
				.sha1(version.sha1)
				.downloadPath(outputFile.toPath());

		MinecraftVersionMeta versionMeta = read(outputFile);
		extension.getMinecraftVersionMeta().set(versionMeta);
		extension.getMinecraftVersionMeta().finalizeValue();

		for (MinecraftVersionMeta.Library library : versionMeta.libraries()) {
			project.getDependencies().add("minecraftLibraries", library.name());
		}
	}

	private static MinecraftVersionMeta read(File file) {
		try (FileReader reader = new FileReader(file)) {
			return FilamentGradlePlugin.OBJECT_MAPPER.readValue(reader, MinecraftVersionMeta.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
