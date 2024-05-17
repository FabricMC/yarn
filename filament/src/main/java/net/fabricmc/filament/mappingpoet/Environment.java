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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.squareup.javapoet.ClassName;

import net.fabricmc.filament.mappingpoet.signature.ClassStaticContext;

/**
 * Represents an overall runtime environment, knows all inner class,
 * super class, etc. information.
 */
public record Environment(
		Map<String, Collection<String>> superTypes,
		Set<String> sealedClasses,
		// declaring classes keep track of namable inner classes
		// and local/anon classes in whole codebase
		Map<String, NestedClassInfo> declaringClasses
) implements ClassStaticContext {
	public record NestedClassInfo(String declaringClass, boolean instanceInner, String simpleName) {
		// two strings are nullable
	}

	public record ClassNamePointer(String simple, String outerClass) {
		public ClassName toClassName(ClassName outerClassName) {
			if (simple == null) {
				return null;
			}

			return outerClassName.nestedClass(simple);
		}
	}

	@Override
	public boolean isInstanceInner(String internalName) {
		var info = declaringClasses.get(internalName);
		return info != null && info.declaringClass != null && info.instanceInner;
	}
}
