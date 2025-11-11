package net.fabricmc.filament.enigma.annotations;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.service.DecompilerInputTransformerService;
import cuchaz.enigma.api.service.GuiService;
import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.ProjectView;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;

public class AnnotationsEnigmaPlugin implements EnigmaPlugin {
	public Path dataPath;
	public AnnotationsData data;
	public ProjectView project;
	public CompletableFuture<AnnotationsIndex> index;

	@Override
	public void init(EnigmaPluginContext ctx) {
		String annotationsPath = System.getProperty("annotations.file");

		if (annotationsPath == null) {
			return;
		}

		dataPath = Path.of(annotationsPath);

		try (Reader reader = Files.newBufferedReader(dataPath)) {
			data = AnnotationsData.read(reader);
		} catch (Exception e) {
			System.err.println("Failed to read annotations file " + annotationsPath + ": " + e);
			return;
		}

		ctx.registerService("annotations:decompiler_input_transformer", DecompilerInputTransformerService.TYPE, () -> new AnnotationsDecompilerInputTransformerService(this));
		ctx.registerService("annotations:gui", GuiService.TYPE, () -> new AnnotationsGuiService(this));
		ctx.registerService("annotations:i18n", AnnotationsI18nService.TYPE, AnnotationsI18nService::new);
		ctx.registerService("annotations:project", ProjectService.TYPE, () -> new AnnotationsProjectService(this));
	}
}
