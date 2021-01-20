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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.Mapped;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mapping.util.EntryTriple;

//Taken from loom
public class MappingsStore {
	private final Map<String, ClassDef> classes = new HashMap<>();
	private final Map<EntryTriple, FieldDef> fields = new HashMap<>();
	private final Map<EntryTriple, Map.Entry<String, MethodDef>> methods = new HashMap<>();

	private final String namespace = "named";
	private final List<String> namespaces;

	public MappingsStore(Path tinyFile) {
		final TinyTree mappings = readMappings(tinyFile);

		namespaces = mappings.getMetadata().getNamespaces();

		for (ClassDef classDef : mappings.getClasses()) {
			final String className = classDef.getName(namespace);
			classes.put(className, classDef);

			for (FieldDef fieldDef : classDef.getFields()) {
				fields.put(new EntryTriple(className, fieldDef.getName(namespace), fieldDef.getDescriptor(namespace)), fieldDef);
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				methods.put(new EntryTriple(className, methodDef.getName(namespace), methodDef.getDescriptor(namespace)), new AbstractMap.SimpleImmutableEntry<>(className, methodDef));
			}
		}
	}

	private static TinyTree readMappings(Path input) {
		try (BufferedReader reader = Files.newBufferedReader(input)) {
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}

	@Deprecated
	public String getClassDoc(String className) {
		ClassDef classDef = classes.get(className);
		return classDef != null ? classDef.getComment() : null;
	}

	private void addDoc(Mapped element, BiConsumer<String, Object[]> addJavadoc) {
		String doc = element.getComment();
		if (doc != null) {
			addJavadoc.accept(doc, new Object[0]);
		}
	}

	public void addClassDoc(BiConsumer<String, Object[]> addJavadoc, String className) {
		ClassDef classDef = classes.get(className);
		if (classDef == null) {
			return;
		}
		addDoc(classDef, addJavadoc);
		addJavadoc.accept("\n", new Object[0]);
		for (String namespace : namespaces) {
			String transformedName = classDef.getName(namespace);
			addJavadoc.accept("@mapping {@literal $L:$L}\n", new Object[] {namespace, transformedName});
		}
	}

	@Deprecated
	public String getFieldDoc(EntryTriple fieldEntry) {
		FieldDef fieldDef = fields.get(fieldEntry);
		return fieldDef != null ? fieldDef.getComment() : null;
	}

	public void addFieldDoc(BiConsumer<String, Object[]> addJavadoc, EntryTriple fieldEntry) {
		FieldDef fieldDef = fields.get(fieldEntry);
		if (fieldDef == null) {
			return;
		}

		addDoc(fieldDef, addJavadoc);
		ClassDef owner = classes.get(fieldEntry.getOwner());
		addJavadoc.accept("\n", new Object[0]);
		for (String namespace : namespaces) {
			String transformedName = fieldDef.getName(namespace);
			String mixinForm = "L" + owner.getName(namespace) + ";" + transformedName + ":" + fieldDef.getDescriptor(namespace);
			addJavadoc.accept("@mapping {@literal $L:$L:$L}\n", new Object[] {namespace, transformedName, mixinForm});
		}
	}

	public Map.Entry<String, String> getParamNameAndDoc(Function<String, Collection<String>> superGetters, EntryTriple methodEntry, int index) {
		Map.Entry<String, MethodDef> found = searchMethod(superGetters, methodEntry);
		if (found != null) {
			MethodDef methodDef = found.getValue();
			if (methodDef.getParameters().isEmpty()) {
				return null;
			}
			return methodDef.getParameters().stream()
					.filter(param -> param.getLocalVariableIndex() == index)
					.map(param -> new AbstractMap.SimpleImmutableEntry<>(param.getName(namespace), param.getComment()))
					.findFirst()
					.orElse(null);
		}
		return null;
	}

	@Deprecated
	public String getMethodDoc(Function<String, Collection<String>> superGetters, EntryTriple methodEntry) {
		Map.Entry<String, MethodDef> methodDef = searchMethod(superGetters, methodEntry);

		if (methodDef != null) {
			return methodDef.getValue().getComment(); // comment doc handled separately by javapoet
		}

		return null;
	}

	public void addMethodDoc(BiConsumer<String, Object[]> addJavadoc, Function<String, Collection<String>> superGetters, EntryTriple methodEntry) {
		Map.Entry<String, MethodDef> found = searchMethod(superGetters, methodEntry);
		if (found == null) {
			return;
		}

		MethodDef methodDef = found.getValue();
		ClassDef owner = classes.get(found.getKey());
		if (!owner.getName(namespace).equals(methodEntry.getOwner())) {
			addJavadoc.accept("{@inheritDoc}", new Object[0]); // todo try this bypass?
		} else {
			addDoc(methodDef, addJavadoc);
		}

		addJavadoc.accept("\n", new Object[0]);
		for (String namespace : namespaces) {
			String transformedName = methodDef.getName(namespace);
			String mixinForm = "L" + owner.getName(namespace) + ";" + transformedName + methodDef.getDescriptor(namespace);
			addJavadoc.accept("@mapping {@literal $L:$L:$L}\n", new Object[] {namespace, transformedName, mixinForm});
		}
	}

	private Map.Entry<String, MethodDef> searchMethod(Function<String, Collection<String>> superGetters, EntryTriple methodEntry) {
		String className = methodEntry.getOwner();
		if (!classes.containsKey(className)) {
			return null;
		}

		if (methods.containsKey(methodEntry)) {
			return methods.get(methodEntry); // Nullable!
		}

		for (String superName : superGetters.apply(className)) {
			EntryTriple triple = new EntryTriple(superName, methodEntry.getName(), methodEntry.getDescriptor());
			Map.Entry<String, MethodDef> ret = searchMethod(superGetters, triple);
			if (ret != null) {
				methods.put(triple, ret);
				return ret;
			}
		}

		methods.put(methodEntry, null);
		return null;
	}
}
