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

package net.fabricmc.filament.nameproposal.field.nameprovider;

import java.util.List;

import net.fabricmc.filament.nameproposal.field.FieldData;

/**
 * A field name provider that applies a list of field name providers in sequence.
 *
 * <p>If the first provider returns a name, that name is used; otherwise, the next
 * provider is tried, and so on.
 *
 * <p>If no provider returns a name, {@code null} is returned.
 */
public record SequenceFieldNameProvider(List<FieldNameProvider> nameProviders) implements FieldNameProvider {
	@Override
	public String getName(FieldData field) {
		for (FieldNameProvider nameProvider : nameProviders) {
			String name = nameProvider.getName(field);
			if (name != null) return name;
		}

		return null;
	}

	@Override
	public String toString() {
		return this.nameProviders.toString();
	}
}
