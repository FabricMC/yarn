package net.fabricmc.filament.task.base;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;

public abstract class FileOutputTask extends FilamentTask {
	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@Inject
	public FileOutputTask() {
		getOutputFile().finalizeValueOnRead();
	}
}
