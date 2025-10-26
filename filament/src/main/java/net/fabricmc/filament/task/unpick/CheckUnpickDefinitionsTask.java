package net.fabricmc.filament.task.unpick;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.inject.Inject;

import daomephsta.unpick.api.ValidatingUnpickV3Visitor;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.constantmappers.datadriven.parser.UnpickSyntaxException;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.FileSystemUtil;

public abstract class CheckUnpickDefinitionsTask extends DefaultTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckUnpickDefinitionsTask.class);
	private static final int CURRENT_UNPICK_VERSION = 4;

	@InputDirectory
	public abstract DirectoryProperty getInput();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(CheckAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getClasspath().setFrom(getClasspath());
		});
	}

	public interface CheckParameters extends WorkParameters {
		DirectoryProperty getInput();
		ConfigurableFileCollection getClasspath();
	}

	public abstract static class CheckAction implements WorkAction<CheckParameters> {
		@Override
		public void execute() {
			List<FileSystemUtil.Delegate> classpathJars = new ArrayList<>();

			try {
				List<IClassResolver> classResolvers = new ArrayList<>();

				for (File file : getParameters().getClasspath().getFiles()) {
					FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(file.toPath());
					classpathJars.add(fileSystem);
					classResolvers.add(ClassResolvers.fromDirectory(fileSystem.getRoot()));
				}

				classResolvers.add(ClassResolvers.classpath(null));

				IClassResolver classResolver = combineClassResolver(classResolvers);

				AtomicInteger failureCount = new AtomicInteger(0);
				getParameters().getInput().getAsFileTree().getFiles().parallelStream().forEach(unpickFile -> {
					if (unpickFile.isDirectory() || !unpickFile.getName().endsWith(".unpick")) {
						return;
					}

					List<UnpickSyntaxException> errors;

					try {
						errors = validateUnpickFile(unpickFile, classResolver, classpathJars);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}

					if (!errors.isEmpty()) {
						Path relativePath = getParameters().getInput().getAsFile().get().toPath().relativize(unpickFile.toPath());

						for (UnpickSyntaxException error : errors) {
							LOGGER.error("{}: {}", relativePath, error.getMessage());
						}

						failureCount.addAndGet(errors.size());
					}
				});

				if (failureCount.get() != 0) {
					throw new UnpickSyntaxException("There were " + failureCount.get() + " unpick check failures, see prior log messages for details");
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				try {
					for (FileSystemUtil.Delegate classpathJar : classpathJars) {
						classpathJar.close();
					}
				} catch (IOException e) {
					// ignore
				}
			}
		}

		private static IClassResolver combineClassResolver(List<IClassResolver> classResolvers) {
			if (classResolvers.isEmpty()) {
				throw new IllegalArgumentException("classResolvers cannot be empty");
			}

			IClassResolver result = classResolvers.getFirst();

			for (int i = 1; i < classResolvers.size(); i++) {
				result = result.chain(classResolvers.get(i));
			}

			return result;
		}

		private static List<UnpickSyntaxException> validateUnpickFile(File file, IClassResolver classResolver, List<FileSystemUtil.Delegate> classpathJars) throws IOException {
			try (UnpickV3Reader reader = new UnpickV3Reader(new FileReader(file))) {
				List<UnpickSyntaxException> errors = new ArrayList<>();
				ValidatingUnpickV3Visitor validator = new ValidatingUnpickV3Visitor(classResolver) {
					@Override
					public void visitHeader(int version) {
						if (version != CURRENT_UNPICK_VERSION) {
							errors.add(new UnpickSyntaxException(1, 1, "Unpick file is v" + version + ", should be v" + CURRENT_UNPICK_VERSION));
						}

						super.visitHeader(version);
					}

					@Override
					public boolean packageExists(String packageName) {
						return CheckAction.packageExists(classpathJars, packageName);
					}
				};
				reader.accept(validator);
				errors.addAll(validator.finishValidation());
				return errors;
			}
		}

		private static boolean packageExists(List<FileSystemUtil.Delegate> classpathJars, String packageName) {
			String packageDir = packageName.replace('.', '/') + "/";

			for (FileSystemUtil.Delegate classpathJar : classpathJars) {
				Path packagePath = classpathJar.getPath(packageDir);

				if (Files.exists(packagePath)) {
					try (Stream<Path> subFiles = Files.list(packagePath)) {
						if (subFiles.anyMatch(file -> file.toString().endsWith(".class"))) {
							return true;
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}

			return false;
		}
	}
}
