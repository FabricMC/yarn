package net.fabricmc.filament.task.enigma;

import java.util.List;

import javax.inject.Inject;

import cuchaz.enigma.command.Command;
import cuchaz.enigma.command.MapSpecializedMethodsCommand;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;

import net.fabricmc.filament.task.base.WithFileOutput;

public abstract class MapSpecializedMethodsTask extends EnigmaCommandTask implements WithFileOutput {
	@InputFile
	public abstract RegularFileProperty getIntermediaryJarFile();

	@Input
	public abstract Property<String> getInputMappingsFormat();

	@InputDirectory
	public abstract DirectoryProperty getMappings();

	@Input
	public abstract Property<String> getOutputMappingsFormat();

	@Inject
	public MapSpecializedMethodsTask() {
		getInputMappingsFormat().convention("engima");
		getOutputMappingsFormat().convention("tinyv2:intermediary:named");
	}

	@Override
	public Class<? extends Command> getCommandClass() {
		return MapSpecializedMethodsCommand.class;
	}

	@Override
	protected List<String> getArguments() {
		return List.of(
				getIntermediaryJarFile().get().getAsFile().getAbsolutePath(),
				getInputMappingsFormat().get(),
				getMappings().get().getAsFile().getAbsolutePath(),
				getOutputMappingsFormat().get(),
				getOutputFile().getAbsolutePath()
		);
	}
}
