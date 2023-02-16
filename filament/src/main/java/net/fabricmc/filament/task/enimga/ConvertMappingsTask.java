package net.fabricmc.filament.task.enimga;

import java.util.List;

import cuchaz.enigma.command.Command;
import cuchaz.enigma.command.ConvertMappingsCommand;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.filament.task.base.WithFileOutput;

public abstract class ConvertMappingsTask extends EnigmaCommandTask implements WithFileInput, WithFileOutput {
	@Input
	public abstract Property<String> getInputMappingsFormat();

	@Input
	public abstract Property<String> getOutputMappingsFormat();

	@Override
	public Class<? extends Command> getCommandClass() {
		return ConvertMappingsCommand.class;
	}

	@Override
	protected List<String> getArguments() {
		return List.of(
			getInputMappingsFormat().get(),
			getInputFile().getAbsolutePath(),
			getOutputMappingsFormat().get(),
			getOutputFile().getAbsolutePath()
		);
	}
}
