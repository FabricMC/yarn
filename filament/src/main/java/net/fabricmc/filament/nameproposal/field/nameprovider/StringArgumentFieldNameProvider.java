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

import java.util.Locale;

import net.fabricmc.filament.nameproposal.field.FieldData;

/**
 * A field name provider that returns a name representing the first string
 * argument of the method call that initializes the field.
 *
 * <p>This provider can be used to name enum fields or fields that are
 * initialized by calling a method that registers the field's value to a map.
 */
public class StringArgumentFieldNameProvider implements FieldNameProvider {
	public static final StringArgumentFieldNameProvider INSTANCE = new StringArgumentFieldNameProvider();

	private StringArgumentFieldNameProvider() {
		return;
	}

	@Override
	public String getName(FieldData field) {
		String s = getFirstStringArg(field);
		if (s == null) return null;

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
		}

		if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
			// Java doesn't allow fields to start with a digit
			return null;
		}

		return s;
	}

	private static String getFirstStringArg(FieldData field) {
		for (Object arg : field.args()) {
			if (arg instanceof String string) {
				return string;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		return "String Argument";
	}
}
