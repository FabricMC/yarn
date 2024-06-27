/*
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.filament.nameproposal.enigma;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.api.service.JarIndexerService;
import cuchaz.enigma.api.service.NameProposalService;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.filament.nameproposal.MappingEntry;
import net.fabricmc.filament.nameproposal.NameFinder;

public class EnigmaNameProposalService implements JarIndexerService, NameProposalService {
	private Map<String, String> recordNames;
	Map<MappingEntry, String> fieldNames;

	@Override
	public void acceptJar(Set<String> classNames, ClassProvider classProvider, JarIndex jarIndex) {
		NameFinder nameFinder = new NameFinder();

		for (String className : classNames) {
			ClassNode classNode = classProvider.get(className);
			nameFinder.accept(Objects.requireNonNull(classNode, "Failed to get ClassNode for " + className));
		}

		recordNames = nameFinder.getRecordNames();
		fieldNames = nameFinder.getFieldNames();
	}

	@Override
	public Optional<String> proposeName(Entry<?> obfEntry, EntryRemapper remapper) {
		Objects.requireNonNull(recordNames, "Cannot proposeName before indexing");

		if (obfEntry instanceof FieldEntry fieldEntry) {
			if (fieldEntry.getName().startsWith("comp_")) {
				return Optional.ofNullable(recordNames.get(fieldEntry.getName()));
			}

			return Optional.ofNullable(fieldNames.get(new MappingEntry(fieldEntry.getContainingClass().getFullName(), fieldEntry.getName(), fieldEntry.getDesc().toString())));
		} else if (obfEntry instanceof MethodEntry methodEntry) {
			if (methodEntry.getName().startsWith("comp_")) {
				return Optional.ofNullable(recordNames.get(methodEntry.getName()));
			}
		}

		return Optional.empty();
	}
}
