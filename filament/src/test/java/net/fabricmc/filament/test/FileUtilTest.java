package net.fabricmc.filament.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.filament.util.FileUtil;

class FileUtilTest {
	@TempDir
	protected File directory;

	@Test
	void deleteIfExists() throws IOException {
		File file = new File(directory, "some-file.txt");
		file.createNewFile();

		FileUtil.deleteIfExists(file);
		assertThat(file).doesNotExist();
	}

	@Test
	void deleteIfExistsMissing() throws IOException {
		File file = new File(directory, "missing-file.txt");

		FileUtil.deleteIfExists(file);
		assertThat(file).doesNotExist();
	}

	@Test
	void write() throws IOException {
		File file = new File(directory, "some-file.txt");

		FileUtil.write(file, "Hello, world!");
		assertThat(file).hasContent("Hello, world!");
	}

	@Test
	void deleteDirectory() throws IOException {
		for (int i = 0; i < 5; i++) {
			File file = new File(directory, "file-" + i + ".txt");
			file.createNewFile();
		}

		File subdirectory = new File(directory, "subdirectory");
		subdirectory.mkdir();

		for (int i = 0; i < 3; i++) {
			File file = new File(subdirectory, "file-" + i + ".txt");
			file.createNewFile();
		}

		FileUtil.deleteDirectory(directory);
		assertThat(directory).doesNotExist();
	}
}
