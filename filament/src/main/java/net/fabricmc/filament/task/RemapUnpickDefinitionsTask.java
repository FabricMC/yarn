package net.fabricmc.filament.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import daomephsta.unpick.constantmappers.datadriven.parser.FieldKey;
import daomephsta.unpick.constantmappers.datadriven.parser.MethodKey;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Remapper;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Writer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.filament.util.FileUtil;
import net.fabricmc.filament.util.UnpickUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class RemapUnpickDefinitionsTask extends DefaultTask {
	@InputFile
	public abstract RegularFileProperty getInput();

	@InputFile
	public abstract RegularFileProperty getMappings();

	@Input
	public abstract Property<String> getSourceNamespace();

	@Input
	public abstract Property<String> getTargetNamespace();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(RemapAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getMappings().set(getMappings());
			parameters.getSourceNamespace().set(getSourceNamespace());
			parameters.getTargetNamespace().set(getTargetNamespace());
			parameters.getOutput().set(getOutput());
		});
	}

	public interface RemapParameters extends WorkParameters {
		@InputFile
		RegularFileProperty getInput();

		@InputFile
		RegularFileProperty getMappings();

		@Input
		Property<String> getSourceNamespace();

		@Input
		Property<String> getTargetNamespace();

		@OutputFile
		RegularFileProperty getOutput();
	}

	public abstract static class RemapAction implements WorkAction<RemapParameters> {
		@Inject
		public RemapAction() {
		}

		@Override
		public void execute() {
			try {
				File output = getParameters().getOutput().getAsFile().get();
				FileUtil.deleteIfExists(output);

				Map<String, String> classMappings = new HashMap<>();
				Map<MethodKey, String> methodMappings = new HashMap<>();
				Map<FieldKey, String> fieldMappings = new HashMap<>();

				final MemoryMappingTree mappingTree = new MemoryMappingTree();
				MappingReader.read(getParameters().getMappings().getAsFile().get().toPath(), mappingTree);

				final int fromM = mappingTree.getNamespaceId(getParameters().getSourceNamespace().get());
				final int toM = mappingTree.getNamespaceId(getParameters().getTargetNamespace().get());

				for (MappingTree.ClassMapping classDef : mappingTree.getClasses()) {
					final String classFromName = classDef.getName(fromM);

					if (classFromName == null) {
						continue;
					}

					classMappings.put(
							classFromName,
							Objects.requireNonNull(classDef.getName(toM), "Null to name: " + classFromName)
					);

					for (MappingTree.MethodMapping methodDef : classDef.getMethods()) {
						methodMappings.put(
								new MethodKey(
										Objects.requireNonNull(classFromName, "Null dst name: " + classDef.getSrcName()),
										Objects.requireNonNull(methodDef.getName(fromM), "Null dst name: " + methodDef.getSrcName()),
										Objects.requireNonNull(methodDef.getDesc(fromM), "Null dst name: " + methodDef.getSrcName())
								),
								Objects.requireNonNull(methodDef.getName(toM), "Null to name: " + methodDef.getSrcName())
						);
					}

					for (MappingTree.FieldMapping fieldDef : classDef.getFields()) {
						fieldMappings.put(
								new FieldKey(
										Objects.requireNonNull(classFromName, "Null dst name: " + classDef.getSrcName()),
										Objects.requireNonNull(fieldDef.getName(fromM), "Null dst name: " + fieldDef.getSrcName())
								),
								Objects.requireNonNull(fieldDef.getName(toM), "Null to name: " + fieldDef.getSrcName())
						);
					}
				}

				try (UnpickV2Reader reader = new UnpickV2Reader(new FileInputStream(getParameters().getInput().getAsFile().get()))) {
					UnpickV2Writer writer = new UnpickV2Writer();
					reader.accept(new UnpickV2Remapper(classMappings, methodMappings, fieldMappings, writer));
					FileUtil.write(output, UnpickUtil.getLfOutput(writer));
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
