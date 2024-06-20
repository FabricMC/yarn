/*
 * Copyright (c) 2016, 2021 FabricMC
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

package net.fabricmc.filament.nameproposal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class NameFinder {
	// comp_x -> name
	private final Map<String, String> recordNames = new HashMap<>();
	private final Map<MappingEntry, String> recordFieldNames = new HashMap<>();
	private final Map<MappingEntry, String> recordMethodNames = new HashMap<>();

	private final Map<String, Set<String>> enumFields = new HashMap<>();
	private final Map<String, List<MethodNode>> methods = new HashMap<>();

	public void accept(ClassNode classNode) {
		classNode.accept(new NameFinderVisitor(Constants.ASM_VERSION, enumFields, methods));

		if ("java/lang/Record".equals(classNode.superName)) {
			classNode.accept(new RecordComponentNameFinder(Constants.ASM_VERSION, recordNames));
		}
	}

	public void acceptIntermediaryMappings(MemoryMappingTree mappingTree) {
		if (recordNames.isEmpty()) {
			return;
		}

		final int intermediaryId = mappingTree.getNamespaceId("intermediary");

		for (Map.Entry<String, String> entry : recordNames.entrySet()) {
			boolean foundMethod = false;
			boolean foundField = false;

			for (MappingTree.ClassMapping classMapping : mappingTree.getClasses()) {
				for (MappingTree.FieldMapping fieldMapping : classMapping.getFields()) {
					if (fieldMapping.getName(intermediaryId).equals(entry.getKey())) {
						MappingEntry fieldEntry = new MappingEntry(classMapping.getName(intermediaryId), fieldMapping.getName(intermediaryId), fieldMapping.getDesc(intermediaryId));
						recordFieldNames.put(fieldEntry, entry.getValue());
						foundField = true;
					}
				}

				for (MappingTree.MethodMapping methodMapping : classMapping.getMethods()) {
					if (methodMapping.getName(intermediaryId).equals(entry.getKey())) {
						MappingEntry fieldEntry = new MappingEntry(classMapping.getName(intermediaryId), methodMapping.getName(intermediaryId), methodMapping.getDesc(intermediaryId));
						recordMethodNames.put(fieldEntry, entry.getValue());
						foundMethod = true;
					}
				}
			}

			if (!foundField) {
				System.err.println("Failed to find field for " + entry);
			}

			if (!foundMethod) {
				System.err.println("Failed to find method for " + entry);
			}
		}
	}

	public Map<String, String> getRecordNames() {
		return recordNames;
	}

	public Map<MappingEntry, String> getFieldNames() {
		Map<MappingEntry, String> fieldNames = new HashMap<>(new FieldNameFinder().findNames(enumFields, methods));
		fieldNames.putAll(recordFieldNames);
		return fieldNames;
	}

	public Map<MappingEntry, String> getMethodNames() {
		return recordMethodNames;
	}
}
