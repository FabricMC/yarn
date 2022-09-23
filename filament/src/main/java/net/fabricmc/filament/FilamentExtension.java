package net.fabricmc.filament;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public abstract class FilamentExtension {
	public static FilamentExtension get(Project project) {
		return project.getExtensions().getByType(FilamentExtension.class);
	}

	@Inject
	protected abstract Project getProject();

	public abstract Property<String> getMinecraftVersion();

	public abstract Property<String> getMinecraftVersionManifestUrl();

	// Populated after evaluation
	public abstract Property<MinecraftVersionMeta> getMinecraftVersionMeta();

	@Inject
	public FilamentExtension() {
		getMinecraftVersion().finalizeValueOnRead();
		getMinecraftVersionManifestUrl().convention("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").finalizeValueOnRead();
	}

	public File getCacheDirectory() {
		return new File(getProject().getRootDir(), ".gradle/filament");
	}

	public File getMinecraftDirectory() {
		return new File(getProject().getRootDir(), ".gradle/filament/" + getMinecraftVersion().get());
	}
}
