package net.fabricmc.filament;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import net.fabricmc.filament.util.MinecraftVersionMetaHelper;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public abstract class FilamentExtension {
	public static FilamentExtension get(Project project) {
		return project.getExtensions().getByType(FilamentExtension.class);
	}

	@Inject
	protected abstract Project getProject();

	public abstract Property<String> getMinecraftVersion();

	public abstract Property<String> getMinecraftVersionManifestUrl();

	private final MinecraftVersionMetaHelper metaHelper;
	private final Provider<MinecraftVersionMeta> metaProvider;

	@Inject
	public FilamentExtension() {
		getMinecraftVersion().finalizeValueOnRead();
		getMinecraftVersionManifestUrl().convention("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").finalizeValueOnRead();

		metaHelper = getProject().getObjects().newInstance(MinecraftVersionMetaHelper.class, this);
		metaProvider = getProject().provider(metaHelper::setup);
	}

	public DirectoryProperty getCacheDirectory() {
		return getProject().getObjects().directoryProperty().fileValue(new File(getProject().getRootDir(), ".gradle/filament"));
	}

	public Provider<Directory> getMinecraftDirectory() {
		return getCacheDirectory().dir(getMinecraftVersion());
	}

	public Provider<RegularFile> getMinecraftFile(String filename) {
		return getMinecraftDirectory().map(directory -> directory.file(filename));
	}

	public Provider<MinecraftVersionMeta> getMinecraftVersionMetadata() {
		return metaProvider;
	}
}
