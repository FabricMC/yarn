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

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.objectweb.asm.tree.ClassNode;

public class ModifierBuilder {
	private final int access;
	private boolean needsUnseal;

	public ModifierBuilder(int access) {
		this.access = access;
	}

	public ModifierBuilder checkUnseal(ClassNode node, Environment env) {
		if (java.lang.reflect.Modifier.isFinal(node.access)) {
			return this;
		}

		if (node.interfaces != null) {
			for (String itf : node.interfaces) {
				if (env.sealedClasses().contains(itf)) {
					needsUnseal = true;
					return this;
				}
			}
		}

		if (node.superName != null && env.sealedClasses().contains(node.superName)) {
			needsUnseal = true;
		}

		return this;
	}

	public Modifier[] getModifiers(Type type) {
		List<Modifier> modifiers = new ArrayList<>();

		if (type == Type.PARAM) {
			if (java.lang.reflect.Modifier.isFinal(access)) {
				modifiers.add(Modifier.FINAL);
			}

			return modifiers.toArray(new Modifier[]{});
		}

		if (java.lang.reflect.Modifier.isPublic(access)) {
			modifiers.add(Modifier.PUBLIC);
		} else if (java.lang.reflect.Modifier.isPrivate(access)) {
			modifiers.add(Modifier.PRIVATE);
		} else if (java.lang.reflect.Modifier.isProtected(access)) {
			modifiers.add(Modifier.PROTECTED);
		}

		if (java.lang.reflect.Modifier.isAbstract(access) && type != Type.ENUM) {
			modifiers.add(Modifier.ABSTRACT);
		}

		if (java.lang.reflect.Modifier.isStatic(access)) {
			modifiers.add(Modifier.STATIC);
		}

		if (!java.lang.reflect.Modifier.isAbstract(access) && !java.lang.reflect.Modifier.isStatic(access) && type == Type.METHOD) {
			modifiers.add(Modifier.DEFAULT);
		}

		if (java.lang.reflect.Modifier.isFinal(access) && type != Type.ENUM && type != Type.RECORD) {
			modifiers.add(Modifier.FINAL);
		}

		if (java.lang.reflect.Modifier.isTransient(access) && type == Type.FIELD) {
			modifiers.add(Modifier.TRANSIENT);
		}

		if (java.lang.reflect.Modifier.isVolatile(access) && type == Type.FIELD) {
			modifiers.add(Modifier.VOLATILE);
		}

		if (java.lang.reflect.Modifier.isSynchronized(access) && type == Type.METHOD) {
			modifiers.add(Modifier.SYNCHRONIZED);
		}

		if (java.lang.reflect.Modifier.isNative(access) && type == Type.METHOD) {
			modifiers.add(Modifier.NATIVE);
		}

		if (java.lang.reflect.Modifier.isStrict(access)) {
			modifiers.add(Modifier.STRICTFP); // obsolete as of Java 17
		}

		if (needsUnseal && type == Type.CLASS) {
			modifiers.add(Modifier.NON_SEALED);
		}

		return modifiers.toArray(new Modifier[]{});
	}

	public static Type getType(boolean enumType, boolean recordType) {
		return enumType ? Type.ENUM : recordType ? Type.RECORD : Type.CLASS;
	}

	public enum Type {
		CLASS,
		ENUM,
		RECORD,
		METHOD,
		FIELD,
		PARAM
	}
}
