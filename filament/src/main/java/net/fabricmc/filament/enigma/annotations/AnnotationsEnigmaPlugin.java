package net.fabricmc.filament.enigma.annotations;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.service.DecompilerInputTransformerService;
import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.ProjectView;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;

public class AnnotationsEnigmaPlugin implements EnigmaPlugin {
	public AnnotationsData data;
	public ProjectView project;

	@Override
	public void init(EnigmaPluginContext ctx) {
		String annotationsPath = System.getProperty("annotations.file");

		if (annotationsPath == null) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(Path.of(annotationsPath))) {
			data = AnnotationsData.read(reader);
		} catch (Exception e) {
			System.err.println("Failed to read annotations file " + annotationsPath + ": " + e);
			return;
		}

		ctx.registerService("annotations:decompiler_input_transformer", DecompilerInputTransformerService.TYPE, () -> new AnnotationsDecompilerInputTransformerService(this));
		ctx.registerService("annotations:project", ProjectService.TYPE, () -> new AnnotationsProjectService(this));
	}
}
