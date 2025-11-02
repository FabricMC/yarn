package net.fabricmc.filament.task;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FileOutputTask;

public abstract class GzipTask extends FileOutputTask {
	@InputFile
	public abstract RegularFileProperty getInput();

	@Input
	public abstract Property<String> getFileName();

	public GzipTask() {
		this.getOutput().convention(
				this.getFileName().zip(this.getProject().getLayout().getBuildDirectory(), (name, dir) -> {
					return dir.file(name + ".gz");
				})
		);
	}

	@TaskAction
	public void gzip() throws Exception {
		Path inputPath = getInput().getAsFile().get().toPath();
		Path outputPath = getOutputPath();

		try (InputStream fis = Files.newInputStream(inputPath);
				OutputStream fos = Files.newOutputStream(outputPath);
				GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
			byte[] buffer = new byte[8192];
			int len;

			while ((len = fis.read(buffer)) != -1) {
				gzos.write(buffer, 0, len);
			}
		}
	}
}
