package net.fabricmc.filament.task.base;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;

public abstract class FileOutputTask extends FilamentTask {
	@OutputFile
	public abstract RegularFileProperty getOutputFile();
}
