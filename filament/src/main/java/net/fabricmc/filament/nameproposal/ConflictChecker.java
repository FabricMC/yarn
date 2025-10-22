/*
 * Copyright (c) 2025 FabricMC
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

import java.util.HashSet;
import java.util.Set;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

public class ConflictChecker<T> {
	// See https://github.com/FabricMC/yarn/pull/4297
	// private static final Logger LOGGER = LoggerFactory.getLogger(ConflictChecker.class);

	private final String type;

	private final Set<String> usedNames = new HashSet<>();
	private final Set<String> conflictingNames = new HashSet<>();

	public ConflictChecker(String type) {
		this.type = type;
	}

	public boolean add(String name, T value) {
		if (!conflictingNames.contains(name) && !usedNames.add(name)) {
			// LOGGER.warn("Warning: Duplicate {} name '{}' was proposed! ({})", this.type, name, value);
			System.out.println("Warning: Duplicate " + this.type + " name '" + name + "' was proposed! (" + value + ")");

			conflictingNames.add(name);
			usedNames.remove(name);
		}

		return usedNames.contains(name);
	}

	@Override
	public String toString() {
		return "ConflictChecker[" + this.type + "]";
	}
}
