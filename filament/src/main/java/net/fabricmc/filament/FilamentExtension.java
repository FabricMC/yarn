package net.fabricmc.filament;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

import java.io.File;

public abstract class FilamentExtension {
	public static FilamentExtension get(Project project) {
		return project.getExtensions().getByType(FilamentExtension.class);
	}

	@Inject
	protected abstract Project getProject();

	public abstract Property<String> getMinecraftVersion();

	public File getCacheDirectory() {
		return new File(getProject().getRootDir(), ".gradle/filament");
	}

	public File getMinecraftDirectory() {
		return new File(getProject().getRootDir(), ".gradle/filament/" + getMinecraftVersion().get());
	}
}
