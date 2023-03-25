package net.fabricmc.filament.task;

import java.io.IOException;
import java.nio.file.Files;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.loom.util.ZipUtils;

public abstract class ExtractZipEntryTask extends FilamentTask implements WithFileInput, WithFileOutput {
	@Input
	public abstract Property<String> getEntry();

	@TaskAction
	public void run() throws IOException {
		byte[] bytes = ZipUtils.unpack(getInputPath(), getEntry().get());
		Files.write(getOutputPath(), bytes);
	}
}
