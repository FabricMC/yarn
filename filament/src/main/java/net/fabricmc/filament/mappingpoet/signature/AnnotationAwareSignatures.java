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

import com.squareup.javapoet.TypeName;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;

public final class AnnotationAwareSignatures {
	private AnnotationAwareSignatures() {
	}

	public static ClassSignature parseClassSignature(String signature, TypeAnnotationMapping annotationMapping, ClassStaticContext context) {
		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(annotationMapping, context, true);
		new SignatureReader(signature).accept(visitor);
		return visitor.collectClass();
	}

	// Note: No receiver (self) parameter included!
	public static MethodSignature parseMethodSignature(String signature, TypeAnnotationMapping annotationMapping, ClassStaticContext context) {
		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(annotationMapping, context, false);
		new SignatureReader(signature).accept(visitor);
		return visitor.collectMethod();
	}

	public static TypeName parseFieldSignature(String signature, TypeAnnotationMapping annotationMapping, ClassStaticContext context) {
		return parseSignature(signature, annotationMapping.getBank(TypeReference.newTypeReference(TypeReference.FIELD)), context);
	}

	public static TypeName parseSignature(String signature, TypeAnnotationBank annotations, ClassStaticContext context) {
		PoetTypeSignatureWriter visitor = new PoetTypeSignatureWriter(annotations, context);
		new SignatureReader(signature).acceptType(visitor);
		return visitor.compute();
	}
}
