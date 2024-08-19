package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;

public abstract class CheckMergedMappingsTask extends FilamentTask implements WithFileInput {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckMergedMappingsTask.class);

	@TaskAction
	public final void run() throws IOException {
		Path path = getInput().get().getAsFile().toPath();
		List<String> errors = new ArrayList<>();

		MappingReader.read(path, new MappingVisitor() {
			private final Set<String> mthDstNames = new HashSet<>();
			private String clsSrcName;
			private String mthSrcName;
			private String mthSrcDesc;

			@Override
			public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
			}

			@Override
			public boolean visitClass(String srcName) throws IOException {
				if (srcName.startsWith("net/minecraft/class_")) {
					errors.add("Encountered mapping for non-existent class " + srcName);
				}

				clsSrcName = srcName;
				return true;
			}

			@Override
			public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
				if (srcName.startsWith("field_")) {
					errors.add("Encountered mapping for non-existent field " + clsSrcName + "#" + srcName + ":" + srcDesc);
				}

				return true;
			}

			@Override
			public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
				mthSrcName = srcName;
				mthSrcDesc = srcDesc;
				mthDstNames.clear();
				return true;
			}

			@Override
			public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
				return true;
			}

			@Override
			public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
				if (targetKind == MappedElementKind.METHOD) {
					mthDstNames.add(name);
				}
			}

			@Override
			public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
				if (targetKind == MappedElementKind.METHOD) {
					// Checking if the srcName is an intermediary name (like for classes and fields) would be the more correct option,
					// but Enigma's bridge method mapper behaves weirdly and injects copies of the mapping into the whole hierarchy.
					// We haven't been able to fix this yet, so in the meantime, we're using the following workaround,
					// which ignores any Enigma-bridge-mapper generated mappings, but should still catch all other
					// mappings without official names.
					if (mthDstNames.size() == 1 && mthDstNames.contains(mthSrcName) && mthSrcName.startsWith("method_")) {
						errors.add("Encountered mapping for non-existent method " + clsSrcName + "#" + mthSrcName + mthSrcDesc);
					}
				}

				return true;
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

		throw new RuntimeException("Mappings for non-existent elements detected");
	}
}
