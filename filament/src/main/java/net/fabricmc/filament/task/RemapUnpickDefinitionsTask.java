package net.fabricmc.filament.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

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
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

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
				String fromM = getParameters().getSourceNamespace().get();
				String toM = getParameters().getTargetNamespace().get();

				try (BufferedReader reader = new BufferedReader(new FileReader(getParameters().getMappings().getAsFile().get()))) {
					TinyTree tinyTree = TinyMappingFactory.loadWithDetection(reader);

					for (ClassDef classDef : tinyTree.getClasses()) {
						classMappings.put(classDef.getName(fromM), classDef.getName(toM));

						for (MethodDef methodDef : classDef.getMethods()) {
							methodMappings.put(
									new MethodKey(classDef.getName(fromM), methodDef.getName(fromM), methodDef.getDescriptor(fromM)),
									methodDef.getName(toM)
							);
						}

						for (FieldDef fieldDef : classDef.getFields()) {
							fieldMappings.put(
									new FieldKey(classDef.getName(fromM), fieldDef.getName(fromM)),
									fieldDef.getName(toM)
							);
						}
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
