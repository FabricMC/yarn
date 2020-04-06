package net.fabricmc.mappingpoet;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Main {

	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("<mappings> <inputJar> <outputDir>");
			return;
		}
		Path mappings = Paths.get(args[0]);
		Path inputJar = Paths.get(args[1]);
		Path outputDirectory = Paths.get(args[2]);

		try {
			if (Files.exists(outputDirectory)) {
				Files.walk(outputDirectory)
						.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			}

			Files.createDirectories(outputDirectory);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (!Files.exists(mappings)) {
			System.out.println("could not find mappings");
			return;
		}

		if (!Files.exists(inputJar)) {
			System.out.println("could not find input jar");
			return;
		}

		generate(mappings, inputJar, outputDirectory);
	}

	public static void generate(Path mappings, Path inputJar, Path outputDirectory) {
		final MappingsStore mapping = new MappingsStore(mappings);
		Map<String, ClassBuilder> classes = new HashMap<>();
		forEachClass(inputJar, classNode -> writeClass(mapping, classNode, classes));

		classes.values().stream()
				.filter(classBuilder -> !classBuilder.getClassName().contains("$"))
				.forEach(classBuilder -> {
					JavaFile javaFile = JavaFile.builder(classBuilder.getClassName().replaceAll("/", ".").substring(0, classBuilder.getClassName().lastIndexOf("/")), classBuilder.build())
							.build();
					try {
						javaFile.writeTo(outputDirectory);
					} catch (IOException e) {
						throw new RuntimeException("Failed to write class", e);
					}
				});


	}

	private static void forEachClass(Path jar, Consumer<ClassNode> classNodeConsumer) {
		List<ClassNode> classes = new ArrayList<>();
		try (final JarFile jarFile = new JarFile(jar.toFile())) {
			Enumeration<JarEntry> entryEnumerator = jarFile.entries();

			while (entryEnumerator.hasMoreElements()) {
				JarEntry entry = entryEnumerator.nextElement();

				if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}

				try(InputStream is = jarFile.getInputStream(entry)) {
					ClassReader reader = new ClassReader(is);
					ClassNode classNode = new ClassNode();
					reader.accept(classNode, 0);

					classes.add(classNode);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//Sort all the classes making sure that inner classes come after the parent classes
		classes.sort(Comparator.comparing(o -> o.name));

		classes.forEach(classNodeConsumer::accept);
	}

	private static void writeClass(MappingsStore mappings, ClassNode classNode, Map<String, ClassBuilder> existingClasses) {
		try {
			//Awful code to skip anonymous classes
			Integer.parseInt(classNode.name.substring(classNode.name.lastIndexOf('$') + 1));
			return;
		} catch (Exception e) {
			//Nope all good if this fails
		}

		ClassBuilder classBuilder = new ClassBuilder(mappings, classNode);

		if (classNode.name.contains("$")) {
			String parentClass = classNode.name.substring(0, classNode.name.lastIndexOf("$"));
			if (!existingClasses.containsKey(parentClass)) {
				throw new RuntimeException("Could not find parent class: " + parentClass + " for " + classNode.name);
			}
			existingClasses.get(parentClass).addInnerClass(classBuilder);
		}

		existingClasses.put(classNode.name, classBuilder);

	}

	private static TypeSpec.Builder createTypeSpecBuilder(ClassNode classNode) {
		return TypeSpec.classBuilder(classNode.name)
				.addModifiers();
	}


}
