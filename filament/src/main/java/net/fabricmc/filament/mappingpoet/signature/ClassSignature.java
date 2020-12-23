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

package net.fabricmc.mappingpoet.signature;

import java.util.List;

import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

// no more a class signature but general super info about class
public final class ClassSignature {
	public final List<TypeVariableName> generics;
	public final TypeName superclass;
	public final List<TypeName> superinterfaces;

	public ClassSignature(List<TypeVariableName> generics, TypeName superclass, List<TypeName> superinterfaces) {
		this.generics = generics;
		this.superclass = superclass;
		this.superinterfaces = superinterfaces;
	}
}
