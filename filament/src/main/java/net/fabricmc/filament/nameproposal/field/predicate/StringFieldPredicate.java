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

package net.fabricmc.filament.nameproposal.field.predicate;

import java.util.Objects;

import net.fabricmc.filament.nameproposal.field.FieldData;

public abstract class StringFieldPredicate implements FieldPredicate {
	protected final String value;

	protected StringFieldPredicate(String value) {
		this.value = Objects.requireNonNull(value);
	}

	protected abstract String getActualValue(FieldData field);

	@Override
	public boolean test(FieldData field) {
		return this.getActualValue(field).equals(this.value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;

		return Objects.equals(this.value, ((StringFieldPredicate) o).value);
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}
}
