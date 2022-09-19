package net.fabricmc.filament.task.base;

import net.fabricmc.filament.FilamentExtension;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

public abstract class FilamentTask extends DefaultTask {
	@Inject
	public FilamentTask() {
		setGroup("filament");
	}

	@Internal
	public FilamentExtension getExtension() {
		return FilamentExtension.get(getProject());
	}
}
