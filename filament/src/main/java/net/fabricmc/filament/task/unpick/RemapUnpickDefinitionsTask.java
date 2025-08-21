package net.fabricmc.filament.task.unpick;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Remapper;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Writer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.fabricmc.filament.util.FileUtil;
import net.fabricmc.filament.util.UnpickUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class RemapUnpickDefinitionsTask extends DefaultTask {
	@InputFile
	public abstract RegularFileProperty getInput();

	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getClasspathNamespace();

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

	public RemapUnpickDefinitionsTask() {
		getClasspathNamespace().convention(getTargetNamespace());
	}

	@TaskAction
	public void run() {
		WorkQueue workQueue = getWorkerExecutor().noIsolation();
		workQueue.submit(RemapAction.class, parameters -> {
			parameters.getInput().set(getInput());
			parameters.getClasspath().setFrom(getClasspath());
			parameters.getClasspathNamespace().set(getClasspathNamespace());
			parameters.getMappings().set(getMappings());
			parameters.getSourceNamespace().set(getSourceNamespace());
			parameters.getTargetNamespace().set(getTargetNamespace());
			parameters.getOutput().set(getOutput());
		});
	}

	public interface RemapParameters extends WorkParameters {
		@InputFile
		RegularFileProperty getInput();

		@InputFiles
		ConfigurableFileCollection getClasspath();

		@Input
		Property<String> getClasspathNamespace();

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

				final MemoryMappingTree mappingTree = new MemoryMappingTree();
				MappingReader.read(getParameters().getMappings().getAsFile().get().toPath(), mappingTree);

				final int fromM = mappingTree.getNamespaceId(getParameters().getSourceNamespace().get());
				final int toM = mappingTree.getNamespaceId(getParameters().getTargetNamespace().get());
				final int classpathM = mappingTree.getNamespaceId(getParameters().getClasspathNamespace().get());

				final JarIndex jarIndex = indexJars(getParameters().getClasspath().getFiles(), mappingTree, fromM, classpathM);

				try (UnpickV3Reader reader = new UnpickV3Reader(new FileReader(getParameters().getInput().getAsFile().get()))) {
					final UnpickV3Writer writer = new UnpickV3Writer();
					reader.accept(new UnpickV3Remapper(writer) {
						@Override
						protected String mapClassName(String className) {
							return mappingTree.mapClassName(className.replace('.', '/'), fromM, toM).replace('/', '.');
						}

						@Override
						protected String mapFieldName(String className, String fieldName, String fieldDesc) {
							final MappingTree.FieldMapping fieldMapping = mappingTree.getField(className.replace('.', '/'), fieldName, fieldDesc, fromM);

							if (fieldMapping == null) {
								return fieldName;
							}

							final String dstName = fieldMapping.getName(toM);
							return dstName == null ? fieldName : dstName;
						}

						@Override
						protected String mapMethodName(String className, String methodName, String methodDesc) {
							final MappingTree.MethodMapping methodMapping = mappingTree.getMethod(className.replace('.', '/'), methodName, methodDesc, fromM);

							if (methodMapping == null) {
								return methodName;
							}

							final String dstName = methodMapping.getName(toM);
							return dstName == null ? methodName : dstName;
						}

						@Override
						protected List<String> getClassesInPackage(String pkg) {
							return jarIndex.classesInPackages.getOrDefault(pkg, List.of());
						}

						@Override
						protected String getFieldDesc(String className, String fieldName) {
							final String fieldKey = className + "." + fieldName;
							String fieldDesc = jarIndex.fieldDescs.get(fieldKey);

							if (fieldDesc == null) {
								// fallback to reflection in the case of jdk fields
								fieldDesc = getFieldDescFromReflection(className, fieldName);

								if (fieldDesc == null) {
									throw new IllegalStateException("Could not get descriptor for field " + fieldKey);
								}
							}

							return fieldDesc;
						}

						@Nullable
						private static String getFieldDescFromReflection(String className, String fieldName) {
							Class<?> clazz;

							try {
								clazz = Class.forName(className, false, null);
							} catch (ClassNotFoundException e) {
								return null;
							}

							Field field;

							try {
								field = clazz.getDeclaredField(fieldName);
							} catch (NoSuchFieldException e) {
								return null;
							}

							return Type.getDescriptor(field.getType());
						}
					});
					FileUtil.write(output, UnpickUtil.getLfOutput(writer));
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private JarIndex indexJars(Collection<File> jarFiles, MemoryMappingTree mappingTree, int fromM, int classpathM) throws IOException {
			final List<JarIndex> indexes = new CopyOnWriteArrayList<>();
			final ThreadLocal<JarIndex> localJarIndex = ThreadLocal.withInitial(() -> {
				JarIndex index = new JarIndex(new HashMap<>(), new HashMap<>());
				indexes.add(index);
				return index;
			});

			List<ZipFile> zips = new ArrayList<>(jarFiles.size());

			try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
				for (File jarFile : jarFiles) {
					zips.add(new ZipFile(jarFile));
				}

				for (ZipFile zip : zips) {
					Enumeration<? extends ZipEntry> entries = zip.entries();

					while (entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();

						if (entry.getName().endsWith(".class")) {
							executor.submit(new ClassIndexTask(mappingTree, classpathM, fromM, localJarIndex) {
								@Override
								protected InputStream getInputStream() throws IOException {
									return zip.getInputStream(entry);
								}
							});
						}
					}
				}
			} finally {
				for (ZipFile zip : zips) {
					try {
						zip.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}

			for (int i = 1; i < indexes.size(); i++) {
				indexes.getFirst().merge(indexes.get(i));
			}

			return indexes.getFirst();
		}
	}

	private abstract static class ClassIndexTask implements Runnable {
		private final MemoryMappingTree mappingTree;
		private final int classpathM;
		private final int fromM;
		private final ThreadLocal<JarIndex> localJarIndex;

		private ClassIndexTask(
				MemoryMappingTree mappingTree,
				int classpathM,
				int fromM,
				ThreadLocal<JarIndex> localJarIndex
		) {
			this.mappingTree = mappingTree;
			this.classpathM = classpathM;
			this.fromM = fromM;
			this.localJarIndex = localJarIndex;
		}

		protected abstract InputStream getInputStream() throws IOException;

		@Override
		public void run() {
			ClassReader reader;

			try {
				reader = new ClassReader(getInputStream());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			reader.accept(new IndexClassVisitor(), ClassReader.SKIP_CODE);
		}

		private class IndexClassVisitor extends ClassVisitor {
			private String className;
			private String mappedClassName;

			IndexClassVisitor() {
				super(Opcodes.ASM9);
			}

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				this.className = name;
				mappedClassName = mappingTree.mapClassName(name, classpathM, fromM);
				int slashIdx = name.lastIndexOf('/');
				String packageName = slashIdx == -1 ? "" : mappedClassName.substring(0, slashIdx);

				localJarIndex.get().classesInPackages
								.computeIfAbsent(packageName.replace('/', '.'), k -> new ArrayList<>())
								.add(mappedClassName.replace('/', '.'));
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				String mappedName = name;
				MappingTree.FieldMapping fieldMapping = mappingTree.getField(className, name, descriptor, classpathM);

				if (fieldMapping != null) {
					mappedName = fieldMapping.getName(fromM);
				}

				String mappedDesc = mappingTree.mapDesc(descriptor, classpathM, fromM);
				localJarIndex.get().fieldDescs.put(mappedClassName.replace('/', '.') + "." + mappedName, mappedDesc);
				return null;
			}
		}
	}

	private record JarIndex(Map<String, List<String>> classesInPackages, Map<String, String> fieldDescs) {
		private void merge(JarIndex other) {
			classesInPackages.putAll(other.classesInPackages);
			fieldDescs.putAll(other.fieldDescs);
		}
	}
}
