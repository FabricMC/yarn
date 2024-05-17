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

package net.fabricmc.filament.mappingpoet;

import static net.fabricmc.mappingio.tree.MappingTreeView.SRC_NAMESPACE_ID;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.ElementMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

//Taken from loom
public class MappingsStore {
	private final MappingTreeView tree;
	private final int maxNamespace;

	public MappingsStore(Path tinyFile) {
		this.tree = readMappings(tinyFile);
		this.maxNamespace = tree.getMaxNamespaceId();
	}

	private static MappingTreeView readMappings(Path input) {
		var tree = new MemoryMappingTree();

		try {
			MappingReader.read(input, MappingFormat.TINY_2_FILE, new MappingSourceNsSwitch(tree, "named"));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}

		return tree;
	}

	private void addDoc(ElementMappingView element, DocAdder adder) {
		String doc = element.getComment();

		if (doc != null) {
			adder.addJavadoc("$L", doc);
		}
	}

	public void addClassDoc(DocAdder adder, String className) {
		var classDef = tree.getClass(className);

		if (classDef == null) {
			return;
		}

		addDoc(classDef, adder);
		adder.addJavadoc("\n");

		for (int id = SRC_NAMESPACE_ID; id < maxNamespace; id++) {
			String transformedName = classDef.getName(id);
			adder.addJavadoc("@mapping {@literal $L:$L}\n", tree.getNamespaceName(id), transformedName);
		}
	}

	public void addFieldDoc(DocAdder addJavadoc, String owner, String name, String desc) {
		var classDef = tree.getClass(owner);

		if (classDef == null) {
			return;
		}

		var fieldDef = classDef.getField(name, desc);

		if (fieldDef == null) {
			return;
		}

		addDoc(fieldDef, addJavadoc);
		addJavadoc.addJavadoc("\n");

		for (int id = SRC_NAMESPACE_ID; id < maxNamespace; id++) {
			String transformedName = fieldDef.getName(id);
			String mixinForm = "L" + classDef.getName(id) + ";" + transformedName + ":" + fieldDef.getDesc(id);
			addJavadoc.addJavadoc("@mapping {@literal $L:$L:$L}\n", tree.getNamespaceName(id), transformedName, mixinForm);
		}
	}

	public Map.Entry<String, String> getParamNameAndDoc(Environment environment, String owner, String name, String desc, int index) {
		var found = searchMethod(environment, owner, name, desc);

		if (found != null) {
			var methodDef = found.getValue();

			if (methodDef.getArgs().isEmpty()) {
				return null;
			}

			return methodDef.getArgs().stream()
					.filter(param -> param.getLvIndex() == index)
					// Map.entry() is null-hostile
					.map(param -> new SimpleImmutableEntry<>(param.getSrcName(), param.getComment()))
					.findFirst()
					.orElse(null);
		}

		return null;
	}

	public void addMethodDoc(DocAdder adder, Environment environment, String owner, String name, String desc) {
		var found = searchMethod(environment, owner, name, desc);

		if (found == null) {
			return;
		}

		var methodDef = found.getValue();
		var ownerDef = found.getKey();

		if (!ownerDef.equals(methodDef.getOwner())) {
			adder.addJavadoc("{@inheritDoc}");
		} else {
			addDoc(methodDef, adder);
		}

		adder.addJavadoc("\n");

		for (int id = SRC_NAMESPACE_ID; id < maxNamespace; id++) {
			String transformedName = methodDef.getName(id);
			String mixinForm = "L" + ownerDef.getName(id) + ";" + transformedName + methodDef.getDesc(id);
			adder.addJavadoc("@mapping {@literal $L:$L:$L}\n", tree.getNamespaceName(id), transformedName, mixinForm);
		}
	}

	private Map.Entry<ClassMappingView, MethodMappingView> searchMethod(Environment environment, String owner, String name, String desc) {
		var classDef = tree.getClass(owner);

		if (classDef == null) {
			return null;
		}

		var methodDef = classDef.getMethod(name, desc);

		if (methodDef != null) {
			return Map.entry(methodDef.getOwner(), methodDef);
		}

		for (String superName : environment.superTypes().getOrDefault(owner, List.of())) {
			var ret = searchMethod(environment, superName, name, desc);

			if (ret != null) {
				return ret;
			}
		}

		return null;
	}

	public interface DocAdder {
		void addJavadoc(String format, Object... args);
	}
}
