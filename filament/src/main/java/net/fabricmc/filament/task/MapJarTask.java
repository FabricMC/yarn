package net.fabricmc.filament.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
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

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public abstract class MapJarTask extends DefaultTask {
	@InputFile public abstract RegularFileProperty getInput();
	@InputFile public abstract RegularFileProperty getMappings();
	@OutputFile public abstract RegularFileProperty getOutput();
	@Classpath public abstract ConfigurableFileCollection getClasspath();
	@Input public abstract Property<String> getFrom();
	@Input public abstract Property<String> getTo();
	@Input public abstract MapProperty<String, String> getClassMappings();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	public MapJarTask() {
		getClassMappings().convention(Map.of());
	}

	@TaskAction
	public void remap() {
		getProject().getLogger().lifecycle(":remapping {} from {} to {}", getInput().get().getAsFile(), getFrom().get(), getTo().get());

		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(RemapAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getMappings().set(getMappings());
			parameters.getOutput().set(getOutput());
			parameters.getClasspath().setFrom(getClasspath());
			parameters.getFrom().set(getFrom());
			parameters.getTo().set(getTo());
			parameters.getClassMappings().set(getClassMappings());
		});
	}

	public interface RemapParameters extends WorkParameters {
		@InputFile RegularFileProperty getInput();
		@InputFile RegularFileProperty getMappings();
		@OutputFile RegularFileProperty getOutput();
		@Classpath ConfigurableFileCollection getClasspath();
		@Input Property<String> getFrom();
		@Input Property<String> getTo();
		@Input MapProperty<String, String> getClassMappings();
	}

	public abstract static class RemapAction implements WorkAction<RemapParameters> {
		@Inject
		public RemapAction() {
		}

		private static Path getPath(Provider<? extends FileSystemLocation> provider) {
			return provider.get().getAsFile().toPath();
		}

		@Override
		public void execute() {
			try {
				doExecute();
			} catch (Exception e) {
				throw new RuntimeException("Failed to remap jar: " + e.getMessage(), e);
			}
		}

		private void doExecute() throws IOException {
			RemapParameters params = getParameters();
			Path output = getPath(params.getOutput());
			Files.deleteIfExists(output);

			TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
					.withMappings(TinyUtils.createTinyMappingProvider(getPath(params.getMappings()), params.getFrom().get(), params.getTo().get()))
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.invalidLvNamePattern(Pattern.compile("\\$\\$\\d+"))
					.inferNameFromSameLvIndex(true);
			remapperBuilder.withMappings(out -> params.getClassMappings().get().forEach(out::acceptClass));
			TinyRemapper remapper = remapperBuilder.build();

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
				Path input = getPath(params.getInput());
				outputConsumer.addNonClassFiles(input);
				remapper.readInputsAsync(input);

				for (File file : params.getClasspath().getFiles()) {
					remapper.readClassPathAsync(file.toPath());
				}

				remapper.apply(outputConsumer);
			} finally {
				remapper.finish();
			}
		}
	}
}
