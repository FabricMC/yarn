/*
 * Copyright (c) 2020 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.mappingpoet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

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
		forEachClass(inputJar, (superGetter, classNode) -> writeClass(mapping, classNode, classes, superGetter));

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

	private static void forEachClass(Path jar, BiConsumer<Function<String, Collection<String>>, ClassNode> classNodeConsumer) {
		List<ClassNode> classes = new ArrayList<>();
		Map<String, Collection<String>> supers = new HashMap<>();
		try (final JarFile jarFile = new JarFile(jar.toFile())) {
			Enumeration<JarEntry> entryEnumerator = jarFile.entries();

			while (entryEnumerator.hasMoreElements()) {
				JarEntry entry = entryEnumerator.nextElement();

				if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}

				try (InputStream is = jarFile.getInputStream(entry)) {
					ClassReader reader = new ClassReader(is);
					ClassNode classNode = new ClassNode();
					reader.accept(classNode, 0);
					List<String> superNames = new ArrayList<>();
					if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
						superNames.add(classNode.superName);
					}
					if (classNode.interfaces != null) {
						superNames.addAll(classNode.interfaces);
					}
					if (!superNames.isEmpty()) {
						supers.put(classNode.name, superNames);
					}

					classes.add(classNode);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//Sort all the classes making sure that inner classes come after the parent classes
		classes.sort(Comparator.comparing(o -> o.name));

		Function<String, Collection<String>> superGetter = k -> supers.getOrDefault(k, Collections.emptyList());
		classes.forEach(node -> classNodeConsumer.accept(superGetter, node));
	}

	private static boolean isDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	private static void writeClass(MappingsStore mappings, ClassNode classNode, Map<String, ClassBuilder> existingClasses, Function<String, Collection<String>> superGetter) {
		String name = classNode.name;
		{
			//Block anonymous class and their nested classes
			int lastSearch = name.length();
			while (lastSearch != -1) {
				lastSearch = name.lastIndexOf('$', lastSearch - 1);
				// names starting with digit is illegal java
				if (isDigit(name.charAt(lastSearch + 1))) {
					return;
				}
			}
		}

		ClassBuilder classBuilder = new ClassBuilder(mappings, classNode, superGetter);

		if (name.contains("$")) {
			String parentClass = name.substring(0, name.lastIndexOf("$"));
			if (!existingClasses.containsKey(parentClass)) {
				throw new RuntimeException("Could not find parent class: " + parentClass + " for " + classNode.name);
			}
			existingClasses.get(parentClass).addInnerClass(classBuilder);
		}

		classBuilder.addMembers();
		existingClasses.put(name, classBuilder);

	}

	private static TypeSpec.Builder createTypeSpecBuilder(ClassNode classNode) {
		return TypeSpec.classBuilder(classNode.name)
				.addModifiers();
	}


}
