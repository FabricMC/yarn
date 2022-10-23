package net.fabricmc.filament.task.minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.loom.util.FileSystemUtil;

public abstract class ExtractBundledServerTask extends FileOutputTask {
	@InputFile
	public abstract RegularFileProperty getServerJar();

	@TaskAction
	public void run() throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(getServerJar().get().getAsFile().toPath(), false)) {
			String versionsList = new String(fs.readAllBytes("META-INF/versions.list"), StandardCharsets.UTF_8);
			String jarPath = "META-INF/versions/" + versionsList.split("\t")[2];
			Files.copy(fs.getPath(jarPath), getOutputFile().get().getAsFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
