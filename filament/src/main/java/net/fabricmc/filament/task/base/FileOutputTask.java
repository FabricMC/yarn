package net.fabricmc.filament.task.base;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;

import javax.inject.Inject;

public abstract class FileOutputTask extends FilamentTask {
	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@Inject
	public FileOutputTask() {
		getOutputFile().finalizeValueOnRead();
	}
}
