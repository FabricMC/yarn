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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class RecordComponentNameFinder extends ClassVisitor {
	static final Handle OBJ_MTH_BOOTSTRAP = new Handle(
			Opcodes.H_INVOKESTATIC,
			"java/lang/runtime/ObjectMethods",
			"bootstrap",
			"""
					(Ljava/lang/invoke/MethodHandles$Lookup;\
					Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;\
					Ljava/lang/Class;Ljava/lang/String;\
					[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;""",
			false
	);
	static final Set<Component> OBJECT_METHODS = Set.of(
			new Component("equals", "(Ljava/lang/Object;)Z"),
			new Component("toString", "()Ljava/lang/String;"),
			new Component("hashCode", "()I")
	);
	// comp_x -> name
	private final Map<String, String> recordNames;
	private String name;

	public RecordComponentNameFinder(int api, Map<String, String> recordNames) {
		super(api);
		this.recordNames = recordNames;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.name = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		var nameAndType = new Component(name, descriptor);

		if (OBJECT_METHODS.contains(nameAndType)) {
			return new ObjectMethodVisitor(api, nameAndType);
		}

		return null;
	}

	private class ObjectMethodVisitor extends MethodVisitor {
		private final String owner;
		private final Component id;

		ObjectMethodVisitor(int api, Component id) {
			super(api);
			this.owner = Objects.requireNonNull(RecordComponentNameFinder.this.name);
			this.id = id;
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

			if (!bootstrapMethodHandle.equals(OBJ_MTH_BOOTSTRAP) || !id.checkIndy(this.owner, name, descriptor)) {
				return;
			}

			assert bootstrapMethodArguments[0] instanceof Type;
			String recordClassName = ((Type) bootstrapMethodArguments[0]).getInternalName();

			if (!recordClassName.equals(owner)) {
				System.out.println("found mismatching object method bootstrap record class in caller class " + owner + "::" + id);
			}

			assert bootstrapMethodArguments[1] instanceof String;
			String[] names = ((String) bootstrapMethodArguments[1]).split(";");
			assert names.length == bootstrapMethodArguments.length - 2;

			for (int i = 2; i < bootstrapMethodArguments.length; i++) {
				if (bootstrapMethodArguments[i] instanceof Handle handle) {
					if (!handle.getName().startsWith("comp_")) {
						// Valid record bytecode, but doesn't have an intermediary name, making it impossible to match up with the record field or method
						continue;
					}

					if (handle.getTag() == Opcodes.H_GETFIELD && handle.getOwner().equals(recordClassName)) {
						var argName = names[i - 2];
						put(recordNames, handle.getName(), argName);
					} else {
						// valid bytecode but we cannot guess
						System.out.println("found special constant pool method handle, cannot process: " + owner + "::" + id);
					}
				} else {
					// valid bytecode, may be condy bsm arg, can't process
					System.out.println("found special bootstrap method arg (maybe condy), cannot process: " + owner + "::" + id);
				}
			}
		}

		private void put(Map<String, String> out, String key, String value) {
			var old = out.put(key, value);

			if (old == null || old.equals(value)) {
				return;
			}

			System.out.println("Found conflicting name for component " + key + " in " + owner + "::"
					+ id + ", replaced " + old + " with " + value);
		}
	}

	private record Component(String name, String desc) {
		boolean checkIndy(String owner, String name, String desc) {
			return this.name.equals(name) && ("(L" + owner + ";" + this.desc.substring(1)).equals(desc);
		}
	}
}
