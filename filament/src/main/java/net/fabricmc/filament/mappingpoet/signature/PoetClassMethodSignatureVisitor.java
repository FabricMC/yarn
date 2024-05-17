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

import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureVisitor;

public final class PoetClassMethodSignatureVisitor extends SignatureVisitor {
	private final TypeAnnotationMapping mapping;
	private final ClassStaticContext context;
	private final boolean forClass;
	ArrayList<TypeVariableName> generics = new ArrayList<>();
	// collecting generic
	String currentGenericName;
	ArrayList<TypeName> currentGenericBounds = new ArrayList<>();
	// bound for each generic
	PoetTypeSignatureWriter pendingLowerBound;

	// classes usage
	ArrayList<TypeName> superTypes = new ArrayList<>();
	PoetTypeSignatureWriter pendingSupertype;

	// methods usage
	ArrayList<TypeName> params = new ArrayList<>();
	ArrayList<TypeName> throwables = new ArrayList<>();
	PoetTypeSignatureWriter pendingSlot;
	TypeName returnType;

	public PoetClassMethodSignatureVisitor(TypeAnnotationMapping mapping, ClassStaticContext context, boolean forClass) {
		super(Opcodes.ASM9);
		this.mapping = mapping;
		this.context = context;
		this.forClass = forClass;
	}

	private void collectGeneric() {
		collectLowerBound();

		if (currentGenericName != null) {
			TypeVariableName generic = TypeVariableName.get(currentGenericName, currentGenericBounds.toArray(new TypeName[0]));
			TypeAnnotationBank bank = mapping.getBank(TypeReference.newTypeParameterReference(forClass ? TypeReference.CLASS_TYPE_PARAMETER : TypeReference.METHOD_TYPE_PARAMETER, generics.size()));
			generic = AnnotationAwareDescriptors.annotate(generic, bank);
			generics.add(generic);

			currentGenericName = null;
			currentGenericBounds.clear();
		}
	}

	private void collectGenerics() {
		// end all generics
		collectGeneric();
	}

	// starts a new generic declaration, like <T> in "<T> T[] toArray(T[] input);"
	@Override
	public void visitFormalTypeParameter(String name) {
		collectGeneric();
		// collect existing type parameter
		// start type var name
		currentGenericName = name;
		currentGenericBounds.clear();
	}

	private void collectLowerBound() {
		if (pendingLowerBound != null) {
			currentGenericBounds.add(pendingLowerBound.compute());
			pendingLowerBound = null;
		}
	}

	private SignatureVisitor visitLowerBound() {
		collectLowerBound();

		TypeAnnotationBank bank = mapping.getBank(TypeReference.newTypeParameterBoundReference(forClass
						? TypeReference.CLASS_TYPE_PARAMETER_BOUND : TypeReference.METHOD_TYPE_PARAMETER_BOUND, generics.size(),
				currentGenericBounds.size()));
		return pendingLowerBound = new PoetTypeSignatureWriter(bank, context);
	}

	@Override
	public SignatureVisitor visitClassBound() {
		return visitLowerBound();
	}

	@Override
	public SignatureVisitor visitInterfaceBound() {
		return visitLowerBound();
	}

	// class exclusive

	private void collectSupertype() {
		if (pendingSupertype != null) {
			TypeName simple = pendingSupertype.compute();
			superTypes.add(simple);

			pendingSupertype = null;
		}
	}

	// always called
	@Override
	public SignatureVisitor visitSuperclass() {
		collectGenerics();
		// don't need to collect other supertype

		return pendingSupertype = new PoetTypeSignatureWriter(mapping.getBank(TypeReference.newSuperTypeReference(-1)), context);
	}

	@Override
	public SignatureVisitor visitInterface() {
		// super class always visited, no generic check
		collectSupertype();

		return pendingSupertype = new PoetTypeSignatureWriter(mapping.getBank(TypeReference.newSuperTypeReference(superTypes.size() - 1)), context);
	}

	public ClassSignature collectClass() {
		collectSupertype();

		TypeName superclass = superTypes.remove(0);
		return new ClassSignature(generics, superclass, superTypes);
	}

	// method exclusive

	private void collectParam() {
		if (pendingSlot != null) {
			TypeName slot = pendingSlot.compute();
			params.add(slot);

			pendingSlot = null;
		}
	}

	private void collectReturnOrThrows() {
		if (pendingSlot != null) {
			if (returnType == null) {
				returnType = pendingSlot.compute();
			} else {
				throwables.add(pendingSlot.compute());
			}

			pendingSlot = null;
		}
	}

	@Override
	public SignatureVisitor visitParameterType() {
		collectGenerics();
		collectParam();

		return pendingSlot = new PoetTypeSignatureWriter(mapping.getBank(TypeReference.newFormalParameterReference(params.size())), context);
	}

	@Override
	public SignatureVisitor visitReturnType() {
		collectGenerics(); // they may skip visiting params, rip!
		collectParam();

		return pendingSlot = new PoetTypeSignatureWriter(mapping.getBank(TypeReference.newTypeReference(TypeReference.METHOD_RETURN)), context);
	}

	@Override
	public SignatureVisitor visitExceptionType() {
		collectReturnOrThrows();

		return pendingSlot = new PoetTypeSignatureWriter(mapping.getBank(TypeReference.newExceptionReference(throwables.size())), context);
	}

	public MethodSignature collectMethod() {
		collectReturnOrThrows();

		return new MethodSignature(generics, params, returnType, throwables);
	}
}
