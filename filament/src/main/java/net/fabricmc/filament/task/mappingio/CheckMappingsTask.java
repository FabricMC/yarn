package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class CheckMappingsTask extends FilamentTask implements WithFileInput {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckMappingsTask.class);

	@TaskAction
	public final void run() throws IOException {
		Path path = getInput().get().getAsFile().toPath();
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(path, tree);

		int minNamespaceId = tree.getMinNamespaceId();
		int maxNamespaceId = tree.getMaxNamespaceId();

		List<String> duplicates = new ArrayList<>();

		for (MappingTree.ClassMapping classMapping : tree.getClasses()) {
			method: for (MappingTree.MethodMapping method : classMapping.getMethods()) {
				Set<String> names = new HashSet<>();
				for (int id = minNamespaceId; id < maxNamespaceId; id++) {
					String name = method.getName(id);

					if (name == null) {
						break method;
					}

					names.add(name);
				}

				if (names.size() == 1 && method.getSrcName().startsWith("method_")) {
					duplicates.add(classMapping.getSrcName() + "." + method.getSrcName());
				}
			}
		}

		if (duplicates.isEmpty()) {
			return;
		}

		for (String duplicate : duplicates) {
			LOGGER.error("Method {} has the same name in all namespaces", duplicate);
		}

		throw new RuntimeException("Duplicate mappings detected");
	}
}
