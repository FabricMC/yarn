package net.fabricmc.filament.task.mappingio;

import java.io.IOException;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;

public abstract class MappingOutputTask extends FilamentTask implements WithFileOutput {
	@Input
	public abstract Property<MappingFormat> getOutputFormat();

	@TaskAction
	public final void run() throws IOException {
		try (MappingWriter mappingWriter = MappingWriter.create(getOutputPath(), getOutputFormat().get())) {
			run(mappingWriter);
		}
	}

	abstract void run(MappingWriter writer) throws IOException;
}
