package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;

public abstract class FormatMappingsTask extends FilamentTask {
	@InputDirectory
	public abstract DirectoryProperty getInput();

	@OutputDirectory
	protected abstract DirectoryProperty getOutput();

	public FormatMappingsTask() {
		getOutput().set(getInput());
	}

	@TaskAction
	void run() throws IOException {
		Path path = getInput().get().getAsFile().toPath();

		MappingWriter writer = MappingWriter.create(path, MappingFormat.ENIGMA_DIR);
		Objects.requireNonNull(writer, "writer");

		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(path, MappingFormat.ENIGMA_DIR, tree);
		tree.accept(writer, VisitOrder.createByName());
	}
}
