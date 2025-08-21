package net.fabricmc.filament.task.unpick;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Writer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.filament.util.FileUtil;
import net.fabricmc.filament.util.UnpickUtil;

public abstract class CombineUnpickDefinitionsTask extends DefaultTask {
	@InputDirectory
	public abstract DirectoryProperty getInput();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(CombineAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getOutput().set(getOutput());
		});
	}

	public interface CombineParameters extends WorkParameters {
		@InputDirectory
		DirectoryProperty getInput();

		@OutputFile
		RegularFileProperty getOutput();
	}

	public abstract static class CombineAction implements WorkAction<CombineParameters> {
		@Inject
		public CombineAction() {
		}

		@Override
		public void execute() {
			try {
				File output = getParameters().getOutput().getAsFile().get();
				FileUtil.deleteIfExists(output);

				UnpickV3Writer writer = new UnpickV3Writer();

				// Sort inputs to get reproducible outputs (also for testing)
				List<File> files = new ArrayList<>(getParameters().getInput().getAsFileTree().getFiles());
				files.sort(Comparator.comparing(File::getName));

				for (File file : files) {
					if (!file.getName().endsWith(".unpick")) {
						continue;
					}

					try (UnpickV3Reader reader = new UnpickV3Reader(new FileReader(file))) {
						reader.accept(writer);
					}
				}

				FileUtil.write(output, UnpickUtil.getLfOutput(writer));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
