package net.fabricmc.filament.enigma.annotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cuchaz.enigma.api.view.ProjectView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public record AnnotationsIndex(Collection<String> annotations, Collection<String> allClasses) {
	public static CompletableFuture<AnnotationsIndex> index(ProjectView project) {
		return CompletableFuture.supplyAsync(() -> {
			List<String> annotations = new ArrayList<>();
			List<String> allClasses = new ArrayList<>(project.getProjectAndLibraryClasses());

			allClasses.parallelStream().forEach(className -> {
				ClassNode bytecode = project.getBytecode(className);

				if (bytecode != null && (bytecode.access & Opcodes.ACC_ANNOTATION) != 0) {
					synchronized (annotations) {
						annotations.add(className);
					}
				}
			});

			indexJdkClasses(annotations, allClasses);

			return new AnnotationsIndex(annotations, allClasses);
		}).whenComplete((annotationsIndex, throwable) -> {
			if (throwable != null) {
				throwable.printStackTrace();
			}
		});
	}

	private static void indexJdkClasses(List<String> annotations, List<String> allClasses) {
		String javaHome = System.getProperty("java.home");
		Path modulesPath = Path.of(javaHome, "jmods");

		try (Stream<Path> modules = Files.walk(modulesPath)) {
			modules.forEach(module -> {
				if (!module.toString().endsWith(".jmod")) {
					return;
				}

				try (ZipFile zipFile = new ZipFile(module.toFile())) {
					List<? extends ZipEntry> classEntries = zipFile.stream()
							.filter(entry -> entry.getName().startsWith("classes/") && entry.getName().endsWith(".class"))
							.toList();

					for (ZipEntry classEntry : classEntries) {
						allClasses.add(getClassName(classEntry));
					}

					classEntries.parallelStream().forEach(entry -> {
						try (InputStream in = zipFile.getInputStream(entry)) {
							ClassReader reader = new ClassReader(in);

							if ((reader.getAccess() & Opcodes.ACC_ANNOTATION) != 0) {
								synchronized (annotations) {
									annotations.add(getClassName(entry));
								}
							}
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String getClassName(ZipEntry entry) {
		String name = entry.getName();
		return name.substring("classes/".length(), name.length() - ".class".length());
	}
}
