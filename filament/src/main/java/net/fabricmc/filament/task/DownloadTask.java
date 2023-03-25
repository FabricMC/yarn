package net.fabricmc.filament.task;

import java.net.URISyntaxException;

import javax.inject.Inject;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadException;

public abstract class DownloadTask extends FilamentTask implements WithFileOutput {
	@Input
	public abstract Property<String> getUrl();
	@Input
	public abstract Property<String> getSha1();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	public DownloadTask() {
		getSha1().convention("");
	}

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(DownloadAction.class, parameters -> {
			parameters.getUrl().set(getUrl());
			parameters.getSha1().set(getSha1());
			parameters.getOutput().set(getOutput());
		});
	}

	public interface DownloadParameters extends WorkParameters {
		Property<String> getUrl();
		Property<String> getSha1();
		RegularFileProperty getOutput();
	}

	public abstract static class DownloadAction implements WorkAction<DownloadParameters> {
		@Override
		public void execute() {
			try {
				var sha1 = getParameters().getSha1().get();
				var download = Download.create(getParameters().getUrl().get());

				if (!sha1.isEmpty()) {
					download.sha1(sha1);
				} else {
					download.defaultCache();
				}

				download.downloadPath(getParameters().getOutput().get().getAsFile().toPath());
			} catch (DownloadException | URISyntaxException e) {
				throw new RuntimeException("Failed to download", e);
			}
		}
	}
}
