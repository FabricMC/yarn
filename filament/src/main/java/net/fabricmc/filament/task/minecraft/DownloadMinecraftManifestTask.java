package net.fabricmc.filament.task.minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.FilamentExtension;
import net.fabricmc.filament.FilamentGradlePlugin;
import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.loom.configuration.providers.minecraft.ManifestVersion;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadException;

public abstract class DownloadMinecraftManifestTask extends FileOutputTask {
	@Input
	public abstract Property<String> getMinecraftVersion();

	@Input
	public abstract Property<String> getVersionManifestUrl();

	@Internal
	public Provider<MinecraftVersionMeta> getVersionMeta() {
		return getOutputFile().map(DownloadMinecraftManifestTask::read);
	}

	@Inject
	public DownloadMinecraftManifestTask() {
		getMinecraftVersion().convention(FilamentExtension.get(getProject()).getMinecraftVersion()).finalizeValueOnRead();
		getVersionManifestUrl().convention("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").finalizeValueOnRead();

		getOutputFile().set(new File(getExtension().getCacheDirectory(), "version_manifest.json"));
	}

	@TaskAction
	public void run() throws URISyntaxException, DownloadException, JsonProcessingException {
		ManifestVersion.Versions version = downloadVersionManifest();

		Download.create(version.url)
				.sha1(version.sha1)
				.downloadPath(getOutputFile().getAsFile().get().toPath());
	}

	private ManifestVersion.Versions downloadVersionManifest() throws URISyntaxException, DownloadException, JsonProcessingException {
		final String versionManifest = Download.create(getVersionManifestUrl().get())
				.defaultCache()
				.downloadString();

		final ManifestVersion mcManifest = FilamentGradlePlugin.OBJECT_MAPPER.readValue(versionManifest, ManifestVersion.class);

		ManifestVersion.Versions version = mcManifest.versions().stream()
				.filter(versions -> versions.id.equalsIgnoreCase(getMinecraftVersion().get()))
				.findFirst()
				.orElse(null);

		if (version == null) {
			throw new RuntimeException("Failed to find minecraft version: " + getMinecraftVersion().get());
		}

		return version;
	}

	private static MinecraftVersionMeta read(RegularFile file) {
		try (FileReader reader = new FileReader(file.getAsFile())) {
			 return FilamentGradlePlugin.OBJECT_MAPPER.readValue(reader, MinecraftVersionMeta.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
