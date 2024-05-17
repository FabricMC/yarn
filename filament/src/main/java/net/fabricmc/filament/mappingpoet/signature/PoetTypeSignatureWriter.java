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
import java.util.Map;
import java.util.Objects;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureVisitor;

import net.fabricmc.filament.mappingpoet.Signatures;

/**
 * A type signature to javapoet visitor.
 *
 * <p>A type signature is one at the usage of type, such as field and local var type.
 * It does not include class or method signatures where new generics like {@code <T>}
 * can be defined.</p>
 */
public final class PoetTypeSignatureWriter extends SignatureVisitor {
	private final ClassStaticContext context;
	private final ArrayList<TypeName> params = new ArrayList<>();
	private /* NonNull */ TypeAnnotationBank storage; // mutable
	private TypeName result;
	// array
	private PoetTypeSignatureWriter arrayChild;
	// class type signature stuff
	private TypeName currentType; // ClassName or ParameterizedTypeName
	private String nestedClassName;
	// single type argument
	private char activeTypeArgumentKind;
	private PoetTypeSignatureWriter activeTypeArgument;

	public PoetTypeSignatureWriter(TypeAnnotationBank storage, ClassStaticContext context) {
		super(Opcodes.ASM9);
		this.storage = storage;
		this.context = context;
	}

	public TypeName compute() {
		// array cleanup. doesn't have visit end TwT
		if (arrayChild != null) {
			result = ArrayTypeName.of(arrayChild.compute());

			result = annotate(result);
		}

		return Objects.requireNonNull(result, "writer did not visit");
	}

	private <T extends TypeName> T annotate(T input) {
		return AnnotationAwareDescriptors.annotate(input, storage);
	}

	private void annotateResult() {
		result = annotate(result);
	}

	// primitives
	@Override
	public void visitBaseType(char descriptor) {
		result = Signatures.getPrimitive(descriptor);
		annotateResult();
	}

	// T, E etc
	@Override
	public void visitTypeVariable(String name) {
		result = TypeVariableName.get(name);
		annotateResult();
	}

	@Override
	public SignatureVisitor visitArrayType() {
		return arrayChild = new PoetTypeSignatureWriter(this.storage.advance(TypePath.ARRAY_ELEMENT, 0), context);
		// post cleanup, annotate in #getResult()
	}

	// outer class, may have instance inner class. ends with visitEnd
	@Override
	public void visitClassType(String internalName) {
		Map.Entry<ClassName, TypeAnnotationBank> entry = AnnotationAwareDescriptors.annotateUpTo(internalName, storage, context);

		currentType = entry.getKey();
		storage = entry.getValue();
		// later collect annotations in #collectPreviousTypeArgumentsAndAnnotations
	}

	// collect info onto this before we append inners
	private void collectPreviousTypeArgumentsAndAnnotations() {
		collectLastTypeArgument();

		if (!params.isEmpty()) {
			if (currentType instanceof ParameterizedTypeName) {
				// top-level handled already
				currentType = ((ParameterizedTypeName) currentType).nestedClass(nestedClassName, params);
			} else { // assume ClassName
				if (nestedClassName == null) { // top-level
					currentType = ParameterizedTypeName.get((ClassName) currentType, params.toArray(new TypeName[0]));
				} else {
					currentType = ParameterizedTypeName.get(((ClassName) currentType).nestedClass(nestedClassName), params.toArray(new TypeName[0]));
				}
			}

			params.clear();
			nestedClassName = null;
		} else if (nestedClassName != null) {
			if (currentType instanceof ParameterizedTypeName) {
				currentType = ((ParameterizedTypeName) currentType).nestedClass(nestedClassName);
			} else {
				currentType = ((ClassName) currentType).nestedClass(nestedClassName);
			}

			nestedClassName = null;
		}

		currentType = annotate(currentType);
	}

	@Override
	public void visitInnerClassType(String name) {
		collectPreviousTypeArgumentsAndAnnotations();
		// collect previous type arguments
		nestedClassName = name;
		storage = storage.advance(TypePath.INNER_TYPE, 0);
	}

	private void collectLastTypeArgument() {
		if (activeTypeArgument != null) {
			TypeName hold = activeTypeArgument.compute();
			TypeName used = switch (activeTypeArgumentKind) {
			case SignatureVisitor.EXTENDS -> WildcardTypeName.subtypeOf(hold);
			case SignatureVisitor.SUPER -> WildcardTypeName.supertypeOf(hold);
			case SignatureVisitor.INSTANCEOF -> hold;
			default -> throw new IllegalStateException(String.format("Illegal type argument wildcard %s", activeTypeArgumentKind));
			};

			used = AnnotationAwareDescriptors.annotate(used, storage.advance(TypePath.TYPE_ARGUMENT, params.size()));
			params.add(used);

			activeTypeArgument = null;
			activeTypeArgumentKind = 0;
		}
	}

	// wildcard ? like in List<?>
	@Override
	public void visitTypeArgument() {
		collectLastTypeArgument();

		TypeName used = WildcardTypeName.subtypeOf(TypeName.OBJECT);
		used = AnnotationAwareDescriptors.annotate(used, storage.advance(TypePath.TYPE_ARGUMENT, params.size()));
		params.add(used);
	}

	// (? extends/ ? super)? ClassType like in Consumer<? super Integer>,
	// Supplier<? extends String>
	@Override
	public SignatureVisitor visitTypeArgument(char wildcard) {
		collectLastTypeArgument();

		activeTypeArgumentKind = wildcard;
		return activeTypeArgument = new PoetTypeSignatureWriter(storage.advance(TypePath.WILDCARD_BOUND, 0), context);
	}

	@Override
	public void visitEnd() {
		collectPreviousTypeArgumentsAndAnnotations();
		// finalize result!
		result = currentType;
		currentType = null;
	}
}
