package net.fabricmc.filament.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import daomephsta.unpick.cli.Main;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.filament.task.base.WithFileOutput;

public abstract class UnpickJarTask extends FilamentTask implements WithFileInput, WithFileOutput {
	@InputFile
	public abstract RegularFileProperty getUnpickDefinition();
	@InputFile
	public abstract RegularFileProperty getConstantsJarFile();
	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(UnpickAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getOutput().set(getOutput());
			parameters.getUnpickDefinition().set(getUnpickDefinition());
			parameters.getConstantJar().set(getConstantsJarFile());
			parameters.getClasspath().from(getClasspath());
		});
	}

	public interface UnpickParameters extends WorkParameters {
		RegularFileProperty getInput();
		RegularFileProperty getOutput();
		RegularFileProperty getUnpickDefinition();
		RegularFileProperty getConstantJar();
		ConfigurableFileCollection getClasspath();
	}

	public abstract static class UnpickAction implements WorkAction<UnpickParameters> {
		@Override
		public void execute() {
			List<String> args = new ArrayList<>();
			args.add(getPath(getParameters().getInput()));
			args.add(getPath(getParameters().getOutput()));
			args.add(getPath(getParameters().getUnpickDefinition()));
			args.add(getPath(getParameters().getConstantJar()));

			for (File file : getParameters().getClasspath().getFiles()) {
				args.add(file.getAbsolutePath());
			}

			try {
				Main.main(args.toArray(String[]::new));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private String getPath(RegularFileProperty fileProperty) {
			return fileProperty.get().getAsFile().getAbsolutePath();
		}
	}
}
