package net.fabricmc.filament.task.mappingio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.hydrate.BridgeMethodHydrator;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.model.ClassProviderRoot;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;

import net.fabricmc.loom.util.Pair;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.enigma.EnigmaDirReader;
import net.fabricmc.mappingio.tree.HierarchyInfoProvider;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

public abstract class PropagateHierarchyTask extends MappingOutputTask {
	@InputFile
	public abstract RegularFileProperty getIntermediaryJarFile();

	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	@InputDirectory
	public abstract DirectoryProperty getMappingsDirectory();

	@Input
	public abstract Property<String> getSrcNamespace();

	@Input
	public abstract Property<String> getDstNamespace();

	@Override
	void run(MappingWriter writer) throws IOException {
		Path intermediaryJar = getIntermediaryJarFile().get().getAsFile().toPath();
		Path mappingsDir = getMappingsDirectory().get().getAsFile().toPath();
		String srcNs = getSrcNamespace().get();
		String dstNs = getDstNamespace().get();

		HypoContext hypo = HypoContext.builder()
				.withProvider(AsmClassDataProvider.of(ClassProviderRoot.fromJar(intermediaryJar)))
				.withContextProvider(AsmClassDataProvider.of(ClassProviderRoot.ofJdk()))
				.withContextProvider(AsmClassDataProvider.of(ClassProviderRoot.fromJars(getClasspath().getFiles().stream().map(File::toPath).toArray(Path[]::new))))
				.build();
		HydrationManager.createDefault()
				.register(BridgeMethodHydrator.create())
				.hydrate(hypo);
		var hierarchyProvider = new HypoHierarchyProvider(hypo, srcNs);

		VisitableMappingTree tree = new MemoryMappingTree();
		EnigmaDirReader.read(mappingsDir, srcNs, dstNs, tree);

		int nsId = tree.getNamespaceId(srcNs);
		assert nsId != MappingTreeView.NULL_NAMESPACE_ID;

		propagateNames(tree, nsId, hierarchyProvider);

		tree.accept(writer, VisitOrder.createByName());
		hypo.close();
	}

	/**
	 * Copy of {@link MemoryMappingTree#propagateNames} (commit c123d0d) that reports duplicates and conflicts.
	 */
	private <T> void propagateNames(VisitableMappingTree tree, int nsId, HierarchyInfoProvider<T> hierarchyProvider) {
		Set<MethodMapping> processed = Collections.newSetFromMap(new IdentityHashMap<>());
		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		for (ClassMapping cls : tree.getClasses()) {
			for (MethodMapping method : cls.getMethods()) {
				String name = method.getName(nsId);
				if (name == null || name.startsWith("<")) continue; // missing name, <clinit> or <init>
				if (!processed.add(method)) continue;

				T hierarchy = hierarchyProvider.getMethodHierarchy(method);
				if (hierarchyProvider.getHierarchySize(hierarchy) <= 1) continue;

				Collection<? extends MethodMapping> hierarchyMethods = hierarchyProvider.getHierarchyMethods(hierarchy, tree);
				if (hierarchyMethods.size() <= 1) continue;

				String[] dstNames = new String[tree.getDstNamespaces().size()];
				List<Pair<Collection<? extends MethodMapping>, Integer>> duplicatesByNs = new ArrayList<>();
				List<Pair<Collection<? extends MethodMapping>, Integer>> conflictsByNs = new ArrayList<>();

				for (int ns = 0; ns < dstNames.length; ns++) {
					boolean duplicateFound = false;

					for (MethodMapping m : hierarchyMethods) {
						String existingName = dstNames[ns];
						String currentName = m.getDstName(ns);

						if (currentName != null) {
							if (existingName != null) {
								if (existingName.equals(currentName)) {
									if (!duplicateFound) {
										duplicatesByNs.add(new Pair<>(hierarchyMethods, ns));
										duplicateFound = true;
									}

									continue;
								} else {
									conflictsByNs.add(new Pair<>(hierarchyMethods, ns));
									break;
								}
							}

							dstNames[ns] = currentName;
						}
					}
				}

				if (!duplicatesByNs.isEmpty()) {
					addBadMappingsToErrorList(duplicatesByNs, tree, warnings, false);
				}

				if (!conflictsByNs.isEmpty()) {
					addBadMappingsToErrorList(conflictsByNs, tree, errors, true);
				}

				for (MethodMapping m : hierarchyMethods) {
					processed.add(m);

					for (int ns = 0; ns < dstNames.length; ns++) {
						String currentName = dstNames[ns];

						if (currentName != null) {
							m.setDstName(currentName.equals(m.getSrcName()) ? null : currentName, ns);
						}
					}
				}
			}
		}

		if (!warnings.isEmpty()) {
			getLogger().warn("Warnings while propagating:\n{}", String.join("\n", warnings));
		}

		if (!errors.isEmpty()) {
			throw new RuntimeException("Failed to propagate:\n" + String.join("\n", errors));
		}
	}

	private static void addBadMappingsToErrorList(List<Pair<Collection<? extends MethodMapping>, Integer>> mappingsByNs, MappingTreeView tree, List<String> targetList, boolean conflicting) {
		for (Pair<Collection<? extends MethodMapping>, Integer> pair : mappingsByNs) {
			Collection<? extends MethodMapping> hierarchyMethods = pair.left();
			int ns = pair.right();
			StringBuilder sb = new StringBuilder("- ")
					.append(conflicting ? "Conflicting" : "Duplicate")
					.append(" names in hierarchy for namespace '")
					.append(tree.getNamespaceName(ns))
					.append("':");

			for (MethodMapping m : hierarchyMethods) {
				if (m.getDstName(ns) == null) {
					continue;
				}

				sb.append("\n  - ")
						.append(m.getOwner().getSrcName())
						.append(".")
						.append(m.getSrcName())
						.append(m.getSrcDesc() != null ? m.getSrcDesc() : "")
						.append(" -> ")
						.append(m.getDstName(ns));
			}

			targetList.add(sb.toString());
		}
	}
}
