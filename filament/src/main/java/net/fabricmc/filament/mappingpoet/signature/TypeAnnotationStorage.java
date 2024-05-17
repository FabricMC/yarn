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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.squareup.javapoet.AnnotationSpec;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.TypeAnnotationNode;

import net.fabricmc.filament.mappingpoet.FieldBuilder;

public final class TypeAnnotationStorage implements TypeAnnotationMapping, TypeAnnotationBank {
	private final int[] targets; // target type and info, only exist in mapping version
	private final String[] paths;
	private final AnnotationSpec[] contents;
	private final int startIndex;
	private final int endIndex;
	private final String currentPath;

	TypeAnnotationStorage(int startIndex, int endIndex, String currentPath, int[] targets, String[] paths, AnnotationSpec[] contents) {
		this.targets = targets;
		this.paths = paths;
		this.contents = contents;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		this.currentPath = currentPath;
	}

	public static Builder builder() {
		return new Builder();
	}

	static int comparePath(TypePath left, TypePath right) {
		int len = Math.min(left.getLength(), right.getLength());

		for (int i = 0; i < len; i++) {
			int leftStep = left.getStep(i);
			int rightStep = right.getStep(i);

			if (leftStep != rightStep) {
				return Integer.compare(leftStep, rightStep);
			}

			int leftStepArg = left.getStepArgument(i);
			int rightStepArg = right.getStepArgument(i);

			if (leftStepArg != rightStepArg) {
				return Integer.compare(leftStepArg, rightStepArg);
			}
		}

		// shorter ones definitely go first!
		return Integer.compare(left.getLength(), right.getLength());
	}

	@Override
	public TypeAnnotationBank advance(int step, int stepArgument) {
		if (currentPath == null) {
			throw new IllegalStateException();
		}

		String suffix;
		switch (step) {
		case TypePath.ARRAY_ELEMENT:
			suffix = "[";
			break;
		case TypePath.INNER_TYPE:
			suffix = ".";
			break;
		case TypePath.WILDCARD_BOUND:
			suffix = "*";
			break;
		case TypePath.TYPE_ARGUMENT:
			suffix = stepArgument + ";";
			break;
		default:
			throw new IllegalArgumentException();
		}

		String check = currentPath.concat(suffix);

		String hiCheck = check.substring(0, check.length() - 1).concat(Character.toString((char) (check.charAt(check.length() - 1) + 1)));

		int low = Arrays.binarySearch(paths, startIndex, endIndex, check);

		if (low < 0) {
			low = -(low + 1);
		}

		// exclusive hi
		int hi = Arrays.binarySearch(paths, startIndex, endIndex, hiCheck);

		if (hi < 0) {
			hi = -(hi + 1);
		}

		return new TypeAnnotationStorage(low, hi, check, null, paths, contents);
	}

	@Override
	public List<AnnotationSpec> getCurrentAnnotations() {
		if (currentPath == null) {
			throw new IllegalStateException();
		}

		int hi = Arrays.binarySearch(paths, startIndex, endIndex, currentPath + '\u0000');

		if (hi < 0) {
			hi = -(hi + 1);
		}

		return Arrays.asList(contents).subList(startIndex, hi);
	}

	@Override
	public boolean isEmpty() {
		return startIndex >= endIndex;
	}

	@Override
	public TypeAnnotationBank getBank(TypeReference reference) {
		if (targets == null) {
			throw new IllegalStateException();
		}

		int target = reference.getValue();
		// inclusive low
		int low = Arrays.binarySearch(targets, startIndex, endIndex, target);

		if (low < 0) {
			low = -(low + 1);
		}

		// exclusive hi
		int hi = Arrays.binarySearch(targets, startIndex, endIndex, target + 1);

		if (hi < 0) {
			hi = -(hi + 1);
		}

		return new TypeAnnotationStorage(low, hi, "", null, paths, contents);
	}

	public static final class Builder {
		final List<Entry> entries = new ArrayList<>();

		Builder() {
		}

		public Builder add(int typeReference, String typePath, AnnotationSpec spec) {
			entries.add(new Entry(typeReference, typePath, spec));
			return this;
		}

		public Builder add(Iterable<TypeAnnotationNode> nodes) {
			if (nodes == null) {
				return this; // thanks asm
			}

			for (TypeAnnotationNode node : nodes) {
				entries.add(new Entry(node.typeRef, node.typePath == null ? "" : node.typePath.toString(), FieldBuilder.parseAnnotation(node)));
			}

			return this;
		}

		public TypeAnnotationMapping build() {
			this.entries.sort(null);
			int len = this.entries.size();

			int[] targets = new int[len];
			String[] paths = new String[len];
			AnnotationSpec[] contents = new AnnotationSpec[len];

			Iterator<Entry> itr = this.entries.iterator();

			for (int i = 0; i < len; i++) {
				Entry entry = itr.next();
				targets[i] = entry.target;
				paths[i] = entry.path;
				contents[i] = entry.content;
			}

			return new TypeAnnotationStorage(0, len, null, targets, paths, contents);
		}

		private static final class Entry implements Comparable<Entry> {
			final int target;
			final String path;
			final AnnotationSpec content;

			Entry(int target, String path, AnnotationSpec content) {
				this.target = target;
				this.path = path;
				this.content = content;
			}

			@Override
			public int compareTo(Entry o) {
				int c0 = Integer.compare(target, o.target);
				if (c0 != 0) return c0;
				return path.compareTo(o.path);
			}
		}
	}
}
