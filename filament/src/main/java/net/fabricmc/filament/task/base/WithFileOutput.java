package net.fabricmc.filament.task.base;

import java.io.File;
import java.nio.file.Path;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;

public interface WithFileOutput {
	@OutputFile
	RegularFileProperty getOutput();

	@Internal
	default File getOutputFile() {
		return getOutput().get().getAsFile();
	}

	@Internal
	default Path getOutputPath() {
		return getOutputFile().toPath();
	}
}
