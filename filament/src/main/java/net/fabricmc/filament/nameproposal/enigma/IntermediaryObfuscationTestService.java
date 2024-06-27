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

package net.fabricmc.filament.nameproposal.enigma;

import cuchaz.enigma.api.service.EnigmaServiceContext;
import cuchaz.enigma.api.service.ObfuscationTestService;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class IntermediaryObfuscationTestService implements ObfuscationTestService {
	private final String prefix, classPrefix, classPackagePrefix, fieldPrefix, methodPrefix, componentPrefix;

	public IntermediaryObfuscationTestService(EnigmaServiceContext<ObfuscationTestService> context) {
		this.prefix = context.getArgument("package").orElse("net/minecraft") + "/";
		this.classPrefix = context.getArgument("classPrefix").orElse("class_");
		this.fieldPrefix = context.getArgument("fieldPrefix").orElse("field_");
		this.methodPrefix = context.getArgument("methodPrefix").orElse("method_");
		this.componentPrefix = context.getArgument("componentPrefix").orElse("comp_");

		this.classPackagePrefix = this.prefix + this.classPrefix;
	}

	@Override
	public boolean testDeobfuscated(Entry<?> entry) {
		if (entry instanceof ClassEntry) {
			ClassEntry ce = (ClassEntry) entry;
			String[] components = ce.getFullName().split("\\$");

			// all obfuscated components are, at their outermost, class_
			String lastComponent = components[components.length - 1];

			if (lastComponent.startsWith(this.classPrefix) || lastComponent.startsWith(this.classPackagePrefix)) {
				return false;
			}
		} else if (entry instanceof FieldEntry) {
			if (entry.getName().startsWith(this.fieldPrefix)) {
				return false;
			}

			if (entry.getName().startsWith(this.componentPrefix)) {
				return false;
			}
		} else if (entry instanceof MethodEntry) {
			if (entry.getName().startsWith(this.methodPrefix)) {
				return false;
			}

			if (entry.getName().startsWith(this.componentPrefix)) {
				return false;
			}
		} else {
			// unknown type
			return false;
		}

		// known type, not obfuscated
		return true;
	}
}
