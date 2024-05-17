package net.fabricmc.filament.task;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.mappingpoet.MappingPoet;

public abstract class MappingPoetTask extends DefaultTask {
	@InputFile
	public abstract RegularFileProperty getMappings();
	@InputFile
	public abstract RegularFileProperty getMinecraftJar();
	@InputFiles
	public abstract ConfigurableFileCollection getLibraries();
	@OutputDirectory
	public abstract DirectoryProperty getOutput();

	@TaskAction
	public void run() {
		MappingPoet.generate(
				getMappings().get().getAsFile().toPath(),
				getMinecraftJar().get().getAsFile().toPath(),
				getOutput().get().getAsFile().toPath(),
				getLibraries().getFiles().stream().map(File::toPath).toList()
		);
	}
}
