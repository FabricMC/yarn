package net.fabricmc.filament.task.annotations;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.AnnotationsData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.validate.AnnotationsDataValidator;
import net.fabricmc.loom.util.FileSystemUtil;

public abstract class CheckAnnotationsTask extends DefaultTask {
	@InputFile
	public abstract RegularFileProperty getInput();

	@Classpath
	public abstract ConfigurableFileCollection getTargetJars();

	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(CheckAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getTargetJars().setFrom(getTargetJars());
			parameters.getClasspath().setFrom(getClasspath());
		});
	}

	public interface CheckParameters extends WorkParameters {
		@InputFile
		RegularFileProperty getInput();

		@Classpath
		ConfigurableFileCollection getTargetJars();

		@Classpath
		ConfigurableFileCollection getClasspath();
	}

	public abstract static class CheckAction implements WorkAction<CheckParameters> {
		@Override
		public void execute() {
			CheckParameters params = getParameters();
			AnnotationsData annotations;
			List<FileSystemUtil.Delegate> targetJars = new ArrayList<>();
			List<FileSystemUtil.Delegate> classpathJars = new ArrayList<>();

			try (Reader reader = Files.newBufferedReader(params.getInput().get().getAsFile().toPath())) {
				annotations = AnnotationsData.read(reader);

				for (File jar : params.getTargetJars().getFiles()) {
					targetJars.add(FileSystemUtil.getJarFileSystem(jar.toPath()));
				}

				for (File jar : params.getClasspath().getFiles()) {
					classpathJars.add(FileSystemUtil.getJarFileSystem(jar.toPath()));
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			try (MyAnnotationsDataValidator validator = new MyAnnotationsDataValidator(targetJars, classpathJars)) {
				if (!validator.checkData(annotations)) {
					throw new IllegalStateException("There were annotation check errors, see log for details");
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private static final class MyAnnotationsDataValidator extends AnnotationsDataValidator implements Closeable {
			private final List<FileSystemUtil.Delegate> targetJars;
			private final Map<String, ClassNode> targetCache = new HashMap<>();
			private final List<FileSystemUtil.Delegate> classpathJars;
			private final Map<String, ClassNode> classpathCache = new HashMap<>();

			MyAnnotationsDataValidator(List<FileSystemUtil.Delegate> targetJars, List<FileSystemUtil.Delegate> classpathJars) {
				this.targetJars = targetJars;
				this.classpathJars = classpathJars;
			}

			@Nullable
			@Override
			protected ClassNode getClass(String name, boolean includeLibraries) {
				ClassNode clazz = targetCache.computeIfAbsent(name, k -> {
					for (FileSystemUtil.Delegate jar : targetJars) {
						ClassNode c = readClass(jar.getPath(name + ".class"));

						if (c != null) {
							return c;
						}
					}

					return null;
				});

				if (clazz != null || !includeLibraries) {
					return clazz;
				}

				return classpathCache.computeIfAbsent(name, k -> {
					for (FileSystemUtil.Delegate jar : classpathJars) {
						ClassNode c = readClass(jar.getPath(name + ".class"));

						if (c != null) {
							return c;
						}
					}

					return null;
				});
			}

			@Nullable
			private static ClassNode readClass(Path path) {
				try (InputStream in = Files.newInputStream(path)) {
					ClassNode clazz = new ClassNode();
					new ClassReader(in).accept(clazz, ClassReader.SKIP_CODE);
					return clazz;
				} catch (NoSuchFileException e) {
					return null;
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			@Override
			public void close() throws IOException {
				for (FileSystemUtil.Delegate jar : targetJars) {
					jar.close();
				}

				for (FileSystemUtil.Delegate jar : classpathJars) {
					jar.close();
				}
			}
		}
	}
}
