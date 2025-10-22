package net.fabricmc.filament.task.annotations;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public abstract class RemapAnnotationsTask extends DefaultTask {
	@InputFile
	public abstract RegularFileProperty getInput();

	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	@InputFile
	public abstract RegularFileProperty getMappings();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(RemapAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getClasspath().setFrom(getClasspath());
			parameters.getMappings().set(getMappings());
			parameters.getSourceNamespace().set(getSourceNamespace());
			parameters.getTargetNamespace().set(getTargetNamespace());
			parameters.getOutput().set(getOutput());
		});
	}

	public interface RemapParameters extends WorkParameters {
		@InputFile
		RegularFileProperty getInput();

		@Classpath
		ConfigurableFileCollection getClasspath();

		@InputFile
		RegularFileProperty getMappings();

		@Input
		Property<String> getSourceNamespace();

		@Input
		Property<String> getTargetNamespace();

		@OutputFile
		RegularFileProperty getOutput();
	}

	public abstract static class RemapAction implements WorkAction<RemapParameters> {
		private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

		@Override
		public void execute() {
			RemapParameters params = getParameters();

			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(TinyUtils.createTinyMappingProvider(getPath(params.getMappings()), params.getSourceNamespace().get(), params.getTargetNamespace().get()))
					.build();

			// Read Minecraft jar as classpath, so that it's able to figure out inheritance of methods
			List<CompletableFuture<?>> readFutures = new ArrayList<>();

			for (File classpathJar : params.getClasspath().getFiles()) {
				readFutures.add(remapper.readClassPathAsync(classpathJar.toPath()));
			}

			for (CompletableFuture<?> readFuture : readFutures) {
				readFuture.join();
			}

			try {
				AnnotationsData annotations;

				try (Reader reader = Files.newBufferedReader(getPath(params.getInput()))) {
					annotations = AnnotationsData.read(reader);
				}

				AnnotationsData remapped = annotations.remap(remapper, params.getTargetNamespace().get());

				try (Writer writer = Files.newBufferedWriter(getPath(params.getOutput()))) {
					GSON.toJson(remapped.toJson(), writer);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private static Path getPath(Provider<? extends FileSystemLocation> provider) {
			return provider.get().getAsFile().toPath();
		}
	}
}
