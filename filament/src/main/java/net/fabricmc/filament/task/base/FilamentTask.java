package net.fabricmc.filament.task.base;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;

import net.fabricmc.filament.FilamentExtension;

public abstract class FilamentTask extends DefaultTask {
	@Inject
	public FilamentTask() {
		setGroup("filament");
	}

	@Internal
	protected FilamentExtension getExtension() {
		return FilamentExtension.get(getProject());
	}
}
