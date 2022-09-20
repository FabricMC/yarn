package net.fabricmc.filament.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Writer;
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

				UnpickV2Writer writer = new UnpickV2Writer();

				// Sort inputs to get reproducible outputs (also for testing)
				List<File> files = new ArrayList<>(getParameters().getInput().getAsFileTree().getFiles());
				files.sort(Comparator.comparing(File::getName));

				for (File file : files) {
					if (!file.getName().endsWith(".unpick")) {
						continue;
					}

					try (UnpickV2Reader reader = new UnpickV2Reader(new FileInputStream(file))) {
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
