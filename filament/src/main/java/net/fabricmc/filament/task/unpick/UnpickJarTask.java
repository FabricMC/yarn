package net.fabricmc.filament.task.unpick;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileInput;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.filament.util.FileUtil;

public abstract class UnpickJarTask extends FilamentTask implements WithFileInput, WithFileOutput {
	@InputFile
	public abstract RegularFileProperty getUnpickDefinition();
	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(UnpickAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getOutput().set(getOutput());
			parameters.getUnpickDefinition().set(getUnpickDefinition());
			parameters.getClasspath().from(getClasspath());
		});
	}

	public interface UnpickParameters extends WorkParameters {
		RegularFileProperty getInput();
		RegularFileProperty getOutput();
		RegularFileProperty getUnpickDefinition();
		ConfigurableFileCollection getClasspath();
	}

	public abstract static class UnpickAction implements WorkAction<UnpickParameters> {
		@Override
		public void execute() {
			File outputFile = getParameters().getOutput().get().getAsFile();

			try {
				FileUtil.deleteIfExists(outputFile);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			List<ZipFile> classpathZips = new ArrayList<>();

			try (
					ZipFile inputZip = new ZipFile(getParameters().getInput().get().getAsFile());
					Reader mappingsReader = new BufferedReader(new FileReader(getParameters().getUnpickDefinition().get().getAsFile()));
					ZipOutputStream outputZip = new ZipOutputStream(new FileOutputStream(outputFile))
			) {
				IClassResolver classResolver = ClassResolvers.jar(inputZip);

				for (File file : getParameters().getClasspath().getFiles()) {
					ZipFile zip = new ZipFile(file);
					classpathZips.add(zip);
					classResolver = classResolver.chain(ClassResolvers.jar(zip));
				}

				classResolver = classResolver.chain(ClassResolvers.classpath());

				ConstantUninliner uninliner = ConstantUninliner.builder()
						.classResolver(classResolver)
						.grouper(ConstantGroupers.dataDriven()
								.classResolver(classResolver)
								.mappingSource(mappingsReader)
								.build())
						.build();

				try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
					List<CompletableFuture<PendingOutputEntry>> entryFutures = new ArrayList<>();
					Enumeration<? extends ZipEntry> inputEntries = inputZip.entries();

					while (inputEntries.hasMoreElements()) {
						ZipEntry entry = inputEntries.nextElement();
						entryFutures.add(CompletableFuture.supplyAsync(() -> {
							try {
								if (entry.isDirectory()) {
									return new PendingOutputEntry(entry.getName(), null);
								} else if (!entry.getName().endsWith(".class")) {
									return new PendingOutputEntry(entry.getName(), inputZip.getInputStream(entry).readAllBytes());
								} else {
									ClassNode clazz = new ClassNode();
									new ClassReader(inputZip.getInputStream(entry)).accept(clazz, 0);
									uninliner.transform(clazz);
									ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
									clazz.accept(writer);
									return new PendingOutputEntry(entry.getName(), writer.toByteArray());
								}
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						}, executor));
					}

					for (CompletableFuture<PendingOutputEntry> entryFuture : entryFutures) {
						PendingOutputEntry entry = entryFuture.join();
						outputZip.putNextEntry(new ZipEntry(entry.name));

						if (entry.data != null) {
							outputZip.write(entry.data);
						}

						outputZip.closeEntry();
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				for (ZipFile classpathZip : classpathZips) {
					try {
						classpathZip.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}

		private record PendingOutputEntry(String name, byte @Nullable [] data) {
		}
	}
}
