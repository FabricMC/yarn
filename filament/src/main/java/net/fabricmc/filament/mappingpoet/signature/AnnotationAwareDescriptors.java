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

package net.fabricmc.filament.mappingpoet.signature;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;

import net.fabricmc.filament.mappingpoet.Signatures;

public final class AnnotationAwareDescriptors {
	private AnnotationAwareDescriptors() {
	}

	// not really signature, but annotated classes
	public static ClassSignature parse(String rawSuper, List<String> rawInterfaces, TypeAnnotationMapping mapping, ClassStaticContext context) {
		ClassName superName = parseType(rawSuper, mapping.getBank(TypeReference.newSuperTypeReference(-1)), context);

		List<TypeName> interfaces = new ArrayList<>(rawInterfaces.size());

		for (ListIterator<String> itr = rawInterfaces.listIterator(); itr.hasNext(); ) {
			int i = itr.nextIndex();
			String item = itr.next();

			ClassName itfName = parseType(item, mapping.getBank(TypeReference.newSuperTypeReference(i)), context);
			interfaces.add(itfName);
		}

		return new ClassSignature(Collections.emptyList(), superName, interfaces);
	}

	// only for descriptor-based ones. Use signature visitor for signature-based ones!
	public static TypeName parseDesc(String desc, TypeAnnotationBank bank, ClassStaticContext context) {
		Deque<List<AnnotationSpec>> arrayAnnotations = new ArrayDeque<>();
		int len = desc.length();
		int index;

		for (index = 0; (index < len) && (desc.charAt(index) == '['); index++) {
			arrayAnnotations.push(bank.getCurrentAnnotations());
			bank = bank.advance(TypePath.ARRAY_ELEMENT, 0);
		}

		TypeName current;

		if (len - index == 1) {
			current = annotate(Signatures.getPrimitive(desc.charAt(index)), bank);
		} else {
			// L ;
			assert desc.charAt(index) == 'L' && desc.charAt(len - 1) == ';';
			current = parseType(desc.substring(index + 1, len - 1), bank, context);
		}

		while (!arrayAnnotations.isEmpty()) {
			current = ArrayTypeName.of(current);
			List<AnnotationSpec> specs = arrayAnnotations.pop();

			if (!specs.isEmpty()) {
				current = current.annotated(specs);
			}
		}

		return current;
	}

	public static ClassName parseType(String internalName, TypeAnnotationBank bank, ClassStaticContext context) {
		Map.Entry<ClassName, TypeAnnotationBank> result = annotateUpTo(internalName, bank, context);
		return annotate(result.getKey(), result.getValue());
	}

	/**
	 * Annotate class name chains until the last element. Useful for making the last
	 * element a parameterized type before annotating it.
	 *
	 * @param internalName the internal name chain
	 * @param bank         the annotation storage
	 * @param context      the context for testing instance inner class
	 * @return the class name ready for parameterization/annotation and the current annotation state
	 */
	static Map.Entry<ClassName, TypeAnnotationBank> annotateUpTo(String internalName, TypeAnnotationBank bank, ClassStaticContext context) {
		if (internalName.startsWith("L") && internalName.endsWith(";")) {
			throw new AssertionError(internalName);
		}

		int slice = internalName.lastIndexOf('/');
		String packageSt = slice < 0 ? "" : internalName.substring(0, slice).replace('/', '.');

		int moneySign = internalName.indexOf('$', slice + 1);

		if (moneySign == -1) {
			return new AbstractMap.SimpleImmutableEntry<>(ClassName.get(packageSt, internalName.substring(slice + 1)), bank);
		}

		ClassName current = ClassName.get(packageSt, internalName.substring(slice + 1, moneySign));

		final int len = internalName.length();
		boolean enteredInner = false;

		for (int i = moneySign; i < len; ) {
			int t = internalName.indexOf('$', i + 1);

			if (t < 0) {
				t = len;
			}

			// do work
			if (!enteredInner && context.isInstanceInner(internalName.substring(0, t))) {
				enteredInner = true; // instance inner classes cannot nest static ones
			}

			if (enteredInner) {
				// annotate parent before we advance
				current = annotate(current, bank);
			}

			current = current.nestedClass(internalName.substring(i + 1, t));

			if (enteredInner) {
				// advance on path as it's instance inner class
				bank = bank.advance(TypePath.INNER_TYPE, 0);
			}

			i = t;
		}

		return new AbstractMap.SimpleImmutableEntry<>(current, bank);
	}

	@SuppressWarnings("unchecked")
	public static <T extends TypeName> T annotate(T input, TypeAnnotationBank storage) {
		List<AnnotationSpec> annotations = storage.getCurrentAnnotations();
		return annotations.isEmpty() ? input : (T) input.annotated(annotations); // it's implemented so
	}
}
