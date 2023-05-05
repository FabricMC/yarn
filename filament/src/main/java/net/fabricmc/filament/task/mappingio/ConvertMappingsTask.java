package net.fabricmc.filament.task.mappingio;

import java.io.IOException;

import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;

public abstract class ConvertMappingsTask extends MappingOutputTask implements WithFileInput {
	@Override
	void run(MappingWriter writer) throws IOException {
		MappingReader.read(getInputPath(), writer);
	}
}
