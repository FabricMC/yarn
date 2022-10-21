package net.fabricmc.filament.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

abstract class ProjectTest {
	@TempDir
	protected File projectDirectory;

	protected final void setupProject(String name, String... extraFiles) {
		try {
			copyProjectFile(name, "build.gradle");
			copyProjectFile(name, "settings.gradle");

			for (String file : extraFiles) {
				copyProjectFile(name, file);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not set up test for project " + name, e);
		}
	}

	protected final void copyYarnV2Data(String fileName) {
		try {
			copyProjectFile("sharedData", "yarn-mappings-v2.tiny", fileName);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not copy Yarn mapping data to " + fileName, e);
		}
	}

	protected final InputStream getProjectFile(String projectName, String file) {
		return ProjectTest.class.getResourceAsStream("/projects/" + projectName + '/' + file);
	}

	protected final String getProjectFileText(String projectName, String file) throws IOException {
		try (InputStream in = getProjectFile(projectName, file)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void copyProjectFile(String projectName, String file) throws IOException {
		copyProjectFile(projectName, file, file);
	}

	private void copyProjectFile(String projectName, String from, String to) throws IOException {
		try (InputStream in = getProjectFile(projectName, from)) {
			Path target = projectDirectory.toPath().resolve(to);
			Files.createDirectories(target.getParent());
			Files.copy(in, target);
		}
	}
}
