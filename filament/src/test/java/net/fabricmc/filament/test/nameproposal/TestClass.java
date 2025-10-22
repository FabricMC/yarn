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

package net.fabricmc.filament.test.nameproposal;

import java.util.function.Function;

public enum TestClass {
	XYZ;

	private static final String field_1 = of("abc");
	private static final Codec<CodecValue> field_2 = new Codec<>(CodecValue::new, CodecValue::value);

	@Override
	public String toString() {
		return field_1 + field_2;
	}

	private static String of(String name) {
		return name;
	}

	private record CodecValue(String value) { }

	private record Codec<T>(Function<String, T> encoder, Function<T, String> decoder) { }
}
