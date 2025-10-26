package net.fabricmc.filament.enigma.unpick;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cuchaz.enigma.api.I18n;
import cuchaz.enigma.api.service.DecompilerInputTransformerService;
import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.ValidatingUnpickV3Visitor;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import daomephsta.unpick.api.constantgroupers.IConstantGrouper;
import daomephsta.unpick.constantmappers.datadriven.parser.UnpickSyntaxException;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Remapper;
import daomephsta.unpick.constantmappers.datadriven.tree.UnpickV3Visitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class UnpickDecompilerInputTransformerService implements DecompilerInputTransformerService {
	private final UnpickEnigmaPlugin plugin;

	public UnpickDecompilerInputTransformerService(UnpickEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public ClassNode transform(ClassNode classNode) {
		ProjectView project = plugin.project;

		if (project == null) {
			return classNode;
		}

		ConstantUninliner uninliner = plugin.uninliner;

		if (uninliner == null) {
			synchronized (UnpickEnigmaPlugin.UNINLINER_CREATION_LOCK) {
				uninliner = plugin.uninliner;

				if (uninliner == null) {
					uninliner = plugin.uninliner = createUninliner(project);
				}
			}
		}

		ClassNode transformed = new ClassNode();
		classNode.accept(transformed);
		uninliner.transform(transformed);
		return transformed;
	}

	private ConstantUninliner createUninliner(ProjectView project) {
		Map<Path, String> unpickFileContents = readUnpickFiles();

		if (unpickFileContents == null) {
			return defaultConstantUninliner();
		}

		if (!validateUnpickFiles(project, unpickFileContents)) {
			return defaultConstantUninliner();
		}

		IClassResolver classResolver = new EnigmaClassResolver(project).chain(ClassResolvers.classpath(null));
		IConstantGrouper grouper = ConstantGroupers.dataDriven()
				.classResolver(classResolver)
				.mappingSource(data -> {
					unpickFileContents.forEach((path, contents) -> {
						try (UnpickV3Reader reader = new UnpickV3Reader(new StringReader(contents))) {
							// Remap our yarn-mapped unpick files to intermediary, so that they can be used for
							// intermediary-mapped class files that are fed into the decompiler by Enigma.
							reader.accept(new EnigmaUnpickYarnToIntermediaryRemapper(data, plugin, project));
						} catch (IOException | UnpickSyntaxException e) {
							throw new AssertionError("Should not get I/O or syntax error after unpick file is already validated", e);
						}
					});
				})
				.build();

		return ConstantUninliner.builder()
				.classResolver(classResolver)
				.grouper(grouper)
				.build();
	}

	@Nullable
	private static Map<Path, String> readUnpickFiles() {
		Map<Path, String> unpickFileContents = new LinkedHashMap<>();

		try {
			Files.walkFileTree(UnpickEnigmaPlugin.unpickDir, new SimpleFileVisitor<>() {
				@Override
				@NotNull
				public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".unpick")) {
						unpickFileContents.put(UnpickEnigmaPlugin.unpickDir.relativize(file), Files.readString(file));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			System.out.println("Failed to read unpick files: " + e.getMessage());
			return null;
		}

		return unpickFileContents;
	}

	private boolean validateUnpickFiles(ProjectView project, Map<Path, String> unpickFileContents) {
		// Unpick files are validated with yarn mappings rather than intermediary. This is to make the errors more
		// readable and directly relevant to what is written in the files. The consequence of this is that the bytecode
		// from the class provider, which is in intermediary mappings, needs to be remapped to yarn before being fed to
		// the unpick validator. Luckily, ASM's remapper is sufficient for this purpose, which is good because tiny
		// remapper doesn't seem to have an API for remapping an individual ClassNode or class file.

		IClassResolver validatingResolver = new IntermediaryToYarnRemappingClassResolver(new EnigmaClassResolver(project), project, new IntermediaryToYarnEnigmaRemapper(project))
				.chain(ClassResolvers.classpath(null));
		ValidatingUnpickV3Visitor globalValidator = new EnigmaUnpickValidator(plugin, validatingResolver);

		boolean[] hadErrors = { false };

		unpickFileContents.forEach((path, contents) -> {
			try (UnpickV3Reader reader = new UnpickV3Reader(new StringReader(contents))) {
				reader.accept(globalValidator);
			} catch (UnpickSyntaxException | IOException e) {
				System.out.println(path + ": " + e.getMessage());
				hadErrors[0] = true;
				return;
			}

			try (UnpickV3Reader reader = new UnpickV3Reader(new StringReader(contents))) {
				ValidatingUnpickV3Visitor localValidator = new EnigmaUnpickValidator(plugin, validatingResolver);
				reader.accept(localValidator);
				List<UnpickSyntaxException> errors = localValidator.finishValidation();

				if (!errors.isEmpty()) {
					hadErrors[0] = true;

					for (UnpickSyntaxException error : errors) {
						System.out.println(path + ": " + error.getMessage());
					}
				}
			} catch (UnpickSyntaxException | IOException e) {
				throw new AssertionError("This error should have already been caught by the global validator", e);
			}
		});

		if (hadErrors[0]) {
			plugin.showUserVisibleError(I18n.translate("unpick.errors.title"), I18n.translate("unpick.errors"));
			return false;
		}

		List<UnpickSyntaxException> globalErrors = globalValidator.finishValidation();

		if (!globalErrors.isEmpty()) {
			for (UnpickSyntaxException error : globalErrors) {
				System.out.println(error.getMessage());
			}

			plugin.showUserVisibleError(I18n.translate("unpick.errors.title"), I18n.translate("unpick.errors"));

			return false;
		}

		return true;
	}

	private static ConstantUninliner defaultConstantUninliner() {
		return ConstantUninliner.builder()
				.classResolver(ClassResolvers.classpath(null))
				.grouper(ConstantGroupers.dataDriven().classResolver(ClassResolvers.classpath(null)).build())
				.build();
	}

	private record EnigmaClassResolver(ProjectView project) implements IClassResolver {
		@Override
		@Nullable
		public ClassNode resolveClass(String internalName) {
			return project.getBytecode(internalName);
		}
	}

	private static class IntermediaryToYarnEnigmaRemapper extends Remapper {
		private final ProjectView project;

		private IntermediaryToYarnEnigmaRemapper(ProjectView project) {
			this.project = project;
		}

		@Override
		public String map(String internalName) {
			return project.deobfuscate(ClassEntryView.create(internalName)).getFullName();
		}

		@Override
		public String mapFieldName(String owner, String name, String descriptor) {
			return project.deobfuscate(FieldEntryView.create(owner, name, descriptor)).getName();
		}

		@Override
		public String mapRecordComponentName(String owner, String name, String desc) {
			return mapFieldName(owner, name, desc);
		}

		@Override
		public String mapMethodName(String owner, String name, String desc) {
			if (!desc.startsWith("(")) { // workaround for Remapper.mapValue calling mapMethodName even if the Handle is a field one
				return mapFieldName(owner, name, desc);
			}

			return project.deobfuscate(MethodEntryView.create(owner, name, desc)).getName();
		}
	}

	private record IntermediaryToYarnRemappingClassResolver(IClassResolver downstream, ProjectView project, Remapper remapper) implements IClassResolver {
		@Override
		@Nullable
		public ClassNode resolveClass(String internalName) {
			ClassNode node = downstream.resolveClass(project.obfuscate(ClassEntryView.create(internalName)).getFullName());

			if (node == null) {
				return null;
			}

			ClassNode remapped = new ClassNode();
			node.accept(new ClassRemapper(remapped, remapper));
			return remapped;
		}
	}

	private static class EnigmaUnpickValidator extends ValidatingUnpickV3Visitor {
		private final UnpickEnigmaPlugin plugin;

		private EnigmaUnpickValidator(UnpickEnigmaPlugin plugin, IClassResolver classResolver) {
			super(classResolver);
			this.plugin = plugin;
		}

		@Override
		public boolean packageExists(String packageName) {
			return plugin.classesInPackages.containsKey(packageName.replace('.', '/'));
		}
	}

	private static final class EnigmaUnpickYarnToIntermediaryRemapper extends UnpickV3Remapper {
		private final UnpickEnigmaPlugin plugin;
		private final ProjectView project;

		private EnigmaUnpickYarnToIntermediaryRemapper(UnpickV3Visitor downstream, UnpickEnigmaPlugin plugin, ProjectView project) {
			super(downstream);
			this.plugin = plugin;
			this.project = project;
		}

		@Override
		protected String mapClassName(String className) {
			return project.obfuscate(ClassEntryView.create(className.replace('.', '/'))).getFullName().replace('/', '.');
		}

		@Override
		protected String mapFieldName(String className, String fieldName, String fieldDesc) {
			return project.obfuscate(FieldEntryView.create(className.replace('.', '/'), fieldName, fieldDesc)).getName();
		}

		@Override
		protected String mapMethodName(String className, String methodName, String methodDesc) {
			return project.obfuscate(MethodEntryView.create(className.replace('.', '/'), methodName, methodDesc)).getName();
		}

		@Override
		protected List<String> getClassesInPackage(String pkg) {
			return plugin.classesInPackages.getOrDefault(pkg.replace('.', '/'), List.of())
					.stream()
					.map(clazz -> pkg + "." + clazz)
					.toList();
		}

		@Override
		protected String getFieldDesc(String className, String fieldName) {
			String obfClassName = project.obfuscate(ClassEntryView.create(className.replace('.', '/'))).getFullName();
			ClassNode bytecode = project.getBytecode(obfClassName);

			if (bytecode == null) {
				throw new IllegalStateException("Could not find class " + className);
			}

			if (bytecode.fields != null) {
				for (FieldNode field : bytecode.fields) {
					String deobfName = project.deobfuscate(FieldEntryView.create(obfClassName, field.name, field.desc)).getName();

					if (deobfName.equals(fieldName)) {
						return fieldDescObfToDeobf(Type.getType(field.desc));
					}
				}
			}

			throw new IllegalStateException("Could not find field " + className + "." + fieldName);
		}

		private String fieldDescObfToDeobf(Type desc) {
			if (desc.getSort() == Type.OBJECT) {
				return "L" + project.deobfuscate(ClassEntryView.create(desc.getInternalName())).getFullName() + ";";
			} else if (desc.getSort() == Type.ARRAY) {
				return "[".repeat(desc.getDimensions()) + fieldDescObfToDeobf(desc.getElementType());
			} else {
				return desc.getDescriptor();
			}
		}
	}
}
