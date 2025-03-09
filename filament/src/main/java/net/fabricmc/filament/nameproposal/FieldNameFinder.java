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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class FieldNameFinder {
	public Map<MappingEntry, String> findNames(Map<String, Set<String>> allEnumFields, Map<String, List<MethodNode>> classes) {
		Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
		Map<MappingEntry, String> fieldNames = new HashMap<>();
		Map<String, Set<String>> fieldNamesUsed = new HashMap<>();
		Map<String, Set<String>> fieldNamesDuplicate = new HashMap<>();

		for (Map.Entry<String, List<MethodNode>> entry : classes.entrySet()) {
			String owner = entry.getKey();
			Set<String> enumFields = allEnumFields.getOrDefault(owner, Collections.emptySet());

			for (MethodNode mn : entry.getValue()) {
				Frame<SourceValue>[] frames;

				try {
					frames = analyzer.analyze(owner, mn);
				} catch (AnalyzerException e) {
					throw new RuntimeException(e);
				}

				InsnList instrs = mn.instructions;

				for (int i = 1; i < instrs.size(); i++) {
					AbstractInsnNode instr1 = instrs.get(i - 1);
					AbstractInsnNode instr2 = instrs.get(i);
					String s = null;

					if (instr2.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) instr2).owner.equals(owner)
							&& (instr1 instanceof MethodInsnNode && ((MethodInsnNode) instr1).owner.equals(owner) || enumFields.contains(((FieldInsnNode) instr2).desc + ((FieldInsnNode) instr2).name))
							&& (instr1.getOpcode() == Opcodes.INVOKESTATIC || (instr1.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(((MethodInsnNode) instr1).name)))) {
						for (int j = 0; j < frames[i - 1].getStackSize(); j++) {
							SourceValue sv = frames[i - 1].getStack(j);

							for (AbstractInsnNode ci : sv.insns) {
								if (ci instanceof LdcInsnNode && ((LdcInsnNode) ci).cst instanceof String) {
									//if (s == null || !s.equals(((LdcInsnNode) ci).cst)) {
									if (s == null) {
										s = (String) (((LdcInsnNode) ci).cst);
										// stringsFound++;
									}
								}
							}
						}
					}

					if (s != null) {
						if (s.contains(":")) {
							s = s.substring(s.indexOf(':') + 1);
						}

						if (s.contains("/")) {
							int separator = s.indexOf('/');
							String sFirst = s.substring(0, separator);
							String sLast;

							if (s.contains(".") && s.indexOf('.') > separator) {
								sLast = s.substring(separator + 1, s.indexOf('.'));
							} else {
								sLast = s.substring(separator + 1);
							}

							if (sFirst.endsWith("s")) {
								sFirst = sFirst.substring(0, sFirst.length() - 1);
							}

							s = sLast + "_" + sFirst;
						}

						String oldS = s;
						boolean hasAlpha = false;

						for (int j = 0; j < s.length(); j++) {
							char c = s.charAt(j);

							if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
								hasAlpha = true;
							}

							if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && !(c == '_')) {
								s = s.substring(0, j) + "_" + s.substring(j + 1);
							} else if (j > 0 && Character.isUpperCase(s.charAt(j)) && Character.isLowerCase(s.charAt(j - 1))) {
								s = s.substring(0, j) + "_" + s.substring(j, j + 1).toLowerCase(Locale.ROOT) + s.substring(j + 1);
							}
						}

						if (hasAlpha) {
							s = s.toUpperCase(Locale.ROOT);

							Set<String> usedNames = fieldNamesUsed.computeIfAbsent(((FieldInsnNode) instr2).owner, (a) -> new HashSet<>());
							Set<String> usedNamesDuplicate = fieldNamesDuplicate.computeIfAbsent(((FieldInsnNode) instr2).owner, (a) -> new HashSet<>());

							if (!usedNamesDuplicate.contains(s)) {
								if (!usedNames.add(s)) {
									System.out.println("Warning: Duplicate key: " + s + " (" + oldS + ")!");
									usedNamesDuplicate.add(s);
									usedNames.remove(s);
								}
							}

							if (usedNames.contains(s)) {
								if (s.equals(((FieldInsnNode) instr2).name)) {
									// No need to map names that are already named what we want to name it.
									continue;
								}

								fieldNames.put(new MappingEntry(((FieldInsnNode) instr2).owner, ((FieldInsnNode) instr2).name, ((FieldInsnNode) instr2).desc), s);
							}
						}
					}
				}
			}
		}

		return fieldNames;
	}
}
