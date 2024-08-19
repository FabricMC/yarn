package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.filament.task.base.FilamentTask;

public abstract class CheckMappingsTask extends FilamentTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckMappingsTask.class);

	@InputDirectory
	abstract DirectoryProperty getInput();

	@TaskAction
	public final void run() throws IOException {
		Path path = getInput().get().getAsFile().toPath();
		List<String> errors = new ArrayList<>();

		MappingReader.read(path, new MappingVisitor() {
			private final Set<String> classes = new HashSet<>();
			private final Set<String> members = new HashSet<>();
			private final Set<Integer> argLvIndices = new HashSet<>();
			private String clsSrcName;
			private String memberSrcName;
			private String memberId;

			@Override
			public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
			}

			@Override
			public boolean visitClass(String srcName) throws IOException {
				if (classes.contains(srcName)) {
					errors.add("Duplicate class mapping for " + srcName);
				} else {
					classes.add(srcName);
				}

				members.clear();
				clsSrcName = srcName;
				return true;
			}

			@Override
			public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
				memberId = srcName + ":" + srcDesc;

				if (members.contains(memberId)) {
					errors.add("Duplicate field mapping for " + clsSrcName + "#" + memberId);
				} else {
					members.add(memberId);
				}

				memberSrcName = srcName;
				return true;
			}

			@Override
			public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
				memberId = srcName + srcDesc;

				if (members.contains(memberId)) {
					errors.add("Duplicate method mapping for " + clsSrcName + "#" + memberId);
				} else {
					members.add(memberId);
				}

				argLvIndices.clear();
				memberSrcName = srcName;
				return true;
			}

			@Override
			public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
				if (argLvIndices.contains(lvIndex)) {
					errors.add("Duplicate parameter mapping for " + clsSrcName + "#" + memberId + ", slot " + lvIndex);
				}

				argLvIndices.add(lvIndex);
				return true;
			}

			@Override
			public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
				switch (targetKind) {
					case CLASS:
						if (clsSrcName.equals(name)) {
							errors.add("Same name in both namespaces for class " + clsSrcName);
						}

						break;
					case FIELD:
					case METHOD:
						if (memberSrcName.equals(name) && (name.startsWith("method_") || name.startsWith("field_"))) {
							errors.add("Same name in both namespaces for member " + clsSrcName + "#" + memberId);
						}

						break;
				}
			}

			@Override
			public void visitComment(MappedElementKind mappedElementKind, String comment) throws IOException {
			}
		});

		if (errors.isEmpty()) {
			return;
		}

		for (String error : errors) {
			LOGGER.error(error);
		}

		throw new RuntimeException("Duplicate mappings detected");
	}
}
