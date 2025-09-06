/*
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.filament.test.nameproposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.filament.nameproposal.Constants;
import net.fabricmc.filament.nameproposal.FieldNameFinder;
import net.fabricmc.filament.nameproposal.MappingEntry;
import net.fabricmc.filament.nameproposal.NameFinderVisitor;
import net.fabricmc.filament.nameproposal.field.nameprovider.ConditionalFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.ConstantFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.FieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.SequenceFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.StringArgumentFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.predicate.DescriptorFieldPredicate;
import net.fabricmc.filament.nameproposal.field.predicate.InternalInitFieldPredicate;
import net.fabricmc.filament.nameproposal.field.predicate.StaticFieldPredicate;

public class FieldTestMain {
	private static final FieldNameProvider EXPECTED_NAME_PROVIDER = new SequenceFieldNameProvider(List.of(
		new ConditionalFieldNameProvider(new ConstantFieldNameProvider("CODEC"), List.of(new StaticFieldPredicate(true), new DescriptorFieldPredicate("Lnet/fabricmc/filament/test/nameproposal/TestClass$Codec;"))),
		new ConditionalFieldNameProvider(StringArgumentFieldNameProvider.INSTANCE, List.of(new StaticFieldPredicate(true), InternalInitFieldPredicate.INSTANCE))
	));

	@Test
	public void fieldNames() throws Throwable {
		var url = FieldTestMain.class.getResource("./TestClass.class");
		var bytes = Files.readAllBytes(Path.of(url.toURI()));

		Map<MappingEntry, String> names = findNames(List.of(bytes), EXPECTED_NAME_PROVIDER);

		Map<MappingEntry, String> expected = Map.ofEntries(
				Map.entry(new MappingEntry("net/fabricmc/filament/test/nameproposal/TestClass", "field_2", "Lnet/fabricmc/filament/test/nameproposal/TestClass$Codec;"), "CODEC"),
				Map.entry(new MappingEntry("net/fabricmc/filament/test/nameproposal/TestClass", "field_1", "Ljava/lang/String;"), "ABC")
		);

		System.out.println("\n\nExpected names: ");
		printMap(expected);

		System.out.println("\n\nFound names: ");
		printMap(names);

		assertEquals(expected, names);
	}

	public Map<MappingEntry, String> findNames(Iterable<byte[]> classes, FieldNameProvider nameProvider) throws Exception {
		Map<String, List<MethodNode>> methods = new HashMap<>();
		Map<String, Set<String>> enumFields = new HashMap<>();

		for (byte[] data : classes) {
			ClassReader reader = new ClassReader(data);
			NameFinderVisitor vClass = new NameFinderVisitor(Constants.ASM_VERSION, enumFields, methods);
			reader.accept(vClass, ClassReader.SKIP_FRAMES);
		}

		return new FieldNameFinder().findNames(enumFields, methods, nameProvider);
	}

	public static void printMap(Map<MappingEntry, String> map) {
		map.forEach((k, v) -> {
			System.out.println(" - " + k + " --> " + v);
		});
	}
}
