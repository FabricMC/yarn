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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

public class NameFinderVisitor extends ClassVisitor {
	private static final String DONT_OBFUSCATE_DESC = "Lnet/minecraft/class_6177;";

	private String owner;
	private boolean notObfuscated;
	private final Map<String, Set<String>> allEnumFields;
	private final Map<String, List<MethodNode>> allMethods;

	public NameFinderVisitor(int api, Map<String, Set<String>> allEnumFields, Map<String, List<MethodNode>> allMethods) {
		super(api);
		this.allMethods = allMethods;
		this.allEnumFields = allEnumFields;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.owner = name;
		this.notObfuscated = false;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (DONT_OBFUSCATE_DESC.equals(descriptor)) {
			this.notObfuscated = true;
		}

		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (notObfuscated) {
			return super.visitField(access, name, descriptor, signature, value);
		}

		if ((access & Opcodes.ACC_ENUM) != 0) {
			if (!allEnumFields.computeIfAbsent(owner, s -> new HashSet<>()).add(descriptor + name)) {
				throw new IllegalArgumentException("Found two enum fields with the same name \"" + name + "\"!");
			}
		}

		return super.visitField(access, name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions) {
		if (notObfuscated) {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		if ("<clinit>".equals(name)) {
			MethodNode node = new MethodNode(api, access, name, descriptor, signature, exceptions);
			allMethods.computeIfAbsent(owner, s -> new ArrayList<>()).add(node);
			return node;
		} else {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}
}
