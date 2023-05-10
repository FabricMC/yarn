package net.fabricmc.filament.task.mappingio;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
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

		fixInnerClasses(mappingTree);

		var nsCompleter = new MappingNsCompleter(writer, Map.of("named", "intermediary"), true);
		var dstReorder = new MappingDstNsReorder(nsCompleter, List.of("intermediary", "named"));
		var sourceNsSwitch = new MappingSourceNsSwitch(dstReorder, "official");
		mappingTree.accept(sourceNsSwitch);
	}

	private void fixInnerClasses(MemoryMappingTree mappingTree) {
		int named = mappingTree.getNamespaceId("named");

		for (MappingTree.ClassMapping entry : mappingTree.getClasses()) {
			String name = entry.getName(named);

			if (name != null) {
				continue;
			}

			entry.setDstName(matchEnclosingClass(entry.getSrcName(), mappingTree), named);
		}
	}

	/*
	 * Takes something like net/minecraft/class_123$class_124 that doesn't have a mapping, tries to find net/minecraft/class_123
	 * , say the mapping of net/minecraft/class_123 is path/to/someclass and then returns a class of the form
	 * path/to/someclass$class124
	 */
	private String matchEnclosingClass(String sharedName, MemoryMappingTree mappingTree) {
		final int named = mappingTree.getNamespaceId("named");
		final String[] path = sharedName.split(Pattern.quote("$"));

		for (int i = path.length - 2; i >= 0; i--) {
			final String currentPath = String.join("$", Arrays.copyOfRange(path, 0, i + 1));
			final MappingTree.ClassMapping match = mappingTree.getClass(currentPath);

			if (match != null && match.getName(named) != null) {
				return match.getName(named) + "$" + String.join("$", Arrays.copyOfRange(path, i + 1, path.length));
			}
		}

		return sharedName;
	}
}
