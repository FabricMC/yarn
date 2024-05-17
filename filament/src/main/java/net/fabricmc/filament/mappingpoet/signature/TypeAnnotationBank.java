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

import java.util.Collections;
import java.util.List;

import com.squareup.javapoet.AnnotationSpec;

// recommended storing with a sorted array + index slicing

/**
 * The collection of type annotations on a specific type. Can be narrowed down through type path.
 */
public interface TypeAnnotationBank {
	TypeAnnotationBank EMPTY = new TypeAnnotationBank() {
		@Override
		public TypeAnnotationBank advance(int step, int stepArgument) {
			return this;
		}

		@Override
		public List<AnnotationSpec> getCurrentAnnotations() {
			return Collections.emptyList();
		}

		@Override
		public boolean isEmpty() {
			return true;
		}
	};

	/**
	 * Make the scope of type annotations smaller.
	 *
	 * @param step         see {@link org.objectweb.asm.TypePath#getStep(int)}
	 * @param stepArgument see {@link org.objectweb.asm.TypePath#getStepArgument(int)}
	 * @return the sliced type annotation storage
	 */
	TypeAnnotationBank advance(int step, int stepArgument);

	/**
	 * Accesses annotations applicable at current type location.
	 *
	 * <p>Do not modify the returned list!</p>
	 *
	 * @return the current annotations to apply
	 */
	List<AnnotationSpec> getCurrentAnnotations();

	/**
	 * Returns if there is no more annotations. Used to check for receiver
	 * declarations.
	 *
	 * @return whether there's no more annotations
	 */
	boolean isEmpty();
}
