package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
			private String clsSrcName;

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
				if (srcName.startsWith("method_")) {
					errors.add("Encountered mapping for non-existent method " + clsSrcName + "#" + srcName + srcDesc);
				}
				
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
