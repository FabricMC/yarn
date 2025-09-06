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

package net.fabricmc.filament.test.nameproposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.filament.nameproposal.Constants;
import net.fabricmc.filament.nameproposal.RecordComponentNameFinder;

public class RecordTest {
	@Test
	public void recordComponentNames() throws Throwable {
		var url = RecordTest.class.getResource("/" + TestRecord.class.getName().replace(".", "/") + ".class");
		var bytes = Files.readAllBytes(Path.of(url.toURI()));

		var reader = new ClassReader(bytes);
		var classNode = new ClassNode();
		reader.accept(classNode, 0);

		renameBsmArgs(classNode);

		Map<String, String> records = new HashMap<>();
		classNode.accept(new RecordComponentNameFinder(Constants.ASM_VERSION, records));

		Map<String, String> expected = Map.ofEntries(
				Map.entry("comp_2", "a"),
				Map.entry("comp_3", "another"),
				Map.entry("comp_4", "data"),
				Map.entry("comp_5", "aBitOfLongName")
		);

		assertEquals(expected, records);
	}

	// Rename the bsm args in equals to "comp_x" names, as they would be when using intermediary
	private void renameBsmArgs(ClassNode classNode) {
		for (MethodNode method : classNode.methods) {
			if (!method.name.equals("equals")) {
				continue;
			}

			for (AbstractInsnNode instruction : method.instructions) {
				if (instruction.getOpcode() != Opcodes.INVOKEDYNAMIC) {
					continue;
				}

				InvokeDynamicInsnNode idi = (InvokeDynamicInsnNode) instruction;

				for (int i = 2; i <= 5; i++) {
					idi.bsmArgs[i] = copyWithName((Handle) idi.bsmArgs[i], "comp_" + i);
				}
			}
		}
	}

	private static Handle copyWithName(Handle handle, String name) {
		return new Handle(handle.getTag(), handle.getOwner(), name, handle.getDesc(), handle.isInterface());
	}
}
