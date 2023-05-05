package net.fabricmc.filament.task.mappingio;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class MergeMappingsTask extends MappingOutputTask {
	@InputFiles
	public abstract ConfigurableFileCollection getMappingInputs();

	@Override
	void run(MappingWriter writer) throws IOException {
		var mappingTree = new MemoryMappingTree();

		for (File file : getMappingInputs().getFiles()) {
			var nsSwitch = new MappingSourceNsSwitch(mappingTree, "intermediary");
			MappingReader.read(file.toPath(), nsSwitch);
		}

		var nsCompleter = new MappingNsCompleter(writer, Map.of("named", "intermediary"), true);
		var dstReorder = new MappingDstNsReorder(nsCompleter, List.of("intermediary", "named"));
		var nsSwitch = new MappingSourceNsSwitch(dstReorder, "official");
		mappingTree.accept(nsSwitch);
	}
}
