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

package net.fabricmc.filament.test.mappingpoet;

import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.signature.SignatureReader;

import net.fabricmc.filament.mappingpoet.signature.ClassSignature;
import net.fabricmc.filament.mappingpoet.signature.MethodSignature;
import net.fabricmc.filament.mappingpoet.signature.PoetClassMethodSignatureVisitor;
import net.fabricmc.filament.mappingpoet.signature.PoetTypeSignatureWriter;
import net.fabricmc.filament.mappingpoet.signature.TypeAnnotationBank;
import net.fabricmc.filament.mappingpoet.signature.TypeAnnotationMapping;
import net.fabricmc.filament.mappingpoet.Signatures;

public class SignaturesTest {
	@Test
	public void testRandomMapType() {
		//signature Ljava/util/Map<Ljava/util/Map$Entry<[[Ljava/lang/String;[Ljava/util/List<[I>;>;[[[D>;
		//Map<Map.Entry<String[][], List<int[]>[]>, double[][][]> map = new HashMap<>();
		String signature = "Ljava/util/Map<Ljava/util/Map$Entry<[[Ljava/lang/String;[Ljava/util/List<[I>;>;[[[D>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(signature, 0);

		Assertions.assertEquals(85, result.getKey().intValue());
		Assertions.assertEquals("java.util.Map<java.util.Map.Entry<java.lang.String[][], java.util.List<int[]>[]>, double[][][]>", result.getValue().toString());

		PoetTypeSignatureWriter writer = new PoetTypeSignatureWriter(TypeAnnotationBank.EMPTY, s -> false);
		new SignatureReader(signature).acceptType(writer);
		Assertions.assertEquals("java.util.Map<java.util.Map.Entry<java.lang.String[][], java.util.List<int[]>[]>, double[][][]>", writer.compute().toString());
	}

	@Test
	public void testCrazyOne() {
		String classSignature = "<B::Ljava/util/Comparator<-TA;>;C:Ljava/lang/ClassLoader;:Ljava/lang/Iterable<*>;>Ljava/lang/Object;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(classSignature, 4);
		Assertions.assertEquals(32, result.getKey().intValue());
		Assertions.assertEquals("java.util.Comparator<? super A>", result.getValue().toString());

		result = Signatures.parseParameterizedType(classSignature, 34);
		Assertions.assertEquals(57, result.getKey().intValue());
		Assertions.assertEquals("java.lang.ClassLoader", result.getValue().toString());

		result = Signatures.parseParameterizedType(classSignature, 58);
		Assertions.assertEquals(81, result.getKey().intValue());
		Assertions.assertEquals("java.lang.Iterable<?>", result.getValue().toString());
	}

	@Test
	public void soo() {
		TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>> local = new TestOuter<Integer>().new Inner<Comparator<Integer>, URLClassLoader>().new ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>();
		local.hashCode();

		// signature Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;
		String signature = "Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(signature, 0);

		Assertions.assertEquals(322, result.getKey().intValue());
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>", result.getValue().toString());

		PoetTypeSignatureWriter writer = new PoetTypeSignatureWriter(TypeAnnotationBank.EMPTY, s -> false);
		new SignatureReader(signature).acceptType(writer);
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>", writer.compute().toString());
	}

	@Test
	public void arrSoo() {
		@SuppressWarnings("unchecked")
		TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>[][] arr = (TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>[][]) new TestOuter<?>.Inner<?, ?>.ExtraInner<?>[0][];
		arr.toString();
		// signature [[Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;
		String arraySignature = "[[Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(arraySignature, 0);

		Assertions.assertEquals(324, result.getKey().intValue());
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>[][]", result.getValue().toString());

		PoetTypeSignatureWriter writer = new PoetTypeSignatureWriter(TypeAnnotationBank.EMPTY, s -> false);
		new SignatureReader(arraySignature).acceptType(writer);
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>[][]", writer.compute().toString());
	}

	@Test
	public void testClassDeeSignature() {
		// signature <D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;
		String classSig = "<D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;";
		Map.Entry<Integer, TypeName> dBound = Signatures.parseParameterizedType(classSig, 4);

		Assertions.assertEquals(102, dBound.getKey().intValue());
		Assertions.assertEquals("java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<B, A, C>>>", dBound.getValue().toString());

		ClassSignature parsed = Signatures.parseClassSignature(classSig);
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("D")), parsed.generics());
		Assertions.assertEquals(ClassName.OBJECT, parsed.superclass());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.superinterfaces());
	}

	@Test
	public void testClassDeeSignatureVisitor() {
		// signature <D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;
		String classSig = "<D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;";

		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(TypeAnnotationMapping.EMPTY, s -> false, true);
		new SignatureReader(classSig).accept(visitor);
		ClassSignature parsed = visitor.collectClass();
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("D")), parsed.generics());
		Assertions.assertEquals(ClassName.OBJECT, parsed.superclass());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.superinterfaces());
	}

	@Test
	public void testCollectionIntFunctionToArraySignature() {
		// signature <T:Ljava/lang/Object;>(Ljava/util/function/IntFunction<[TT;>;)[TT;
		String methodSignature = "<T:Ljava/lang/Object;>(Ljava/util/function/IntFunction<[TT;>;)[TT;";
		MethodSignature parsed = Signatures.parseMethodSignature(methodSignature);
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("T")), parsed.generics());
		Assertions.assertEquals(ArrayTypeName.of(TypeVariableName.get("T")), parsed.result());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.thrown());
		Assertions.assertIterableEquals(Collections.singleton(ParameterizedTypeName.get(ClassName.get(IntFunction.class), ArrayTypeName.of(TypeVariableName.get("T")))), parsed.parameters());
	}

	@Test
	public void testCollectionArrayToArraySignature() {
		// signature <T:Ljava/lang/Object;>([TT;)[TT;
		String methodSignature = "<T:Ljava/lang/Object;>([TT;)[TT;";
		MethodSignature parsed = Signatures.parseMethodSignature(methodSignature);
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("T")), parsed.generics());
		Assertions.assertEquals(ArrayTypeName.of(TypeVariableName.get("T")), parsed.result());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.thrown());
		Assertions.assertIterableEquals(Collections.singleton(ArrayTypeName.of(TypeVariableName.get("T"))), parsed.parameters());
	}

	@Test
	public void testCollectionArrayToArraySignatureVisitor() {
		// signature <T:Ljava/lang/Object;>([TT;)[TT;
		String methodSignature = "<T:Ljava/lang/Object;>([TT;)[TT;";
		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(TypeAnnotationMapping.EMPTY, s -> false, false);
		new SignatureReader(methodSignature).accept(visitor);
		MethodSignature parsed = visitor.collectMethod();
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("T")), parsed.generics());
		Assertions.assertEquals(ArrayTypeName.of(TypeVariableName.get("T")), parsed.result());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.thrown());
		Assertions.assertIterableEquals(Collections.singleton(ArrayTypeName.of(TypeVariableName.get("T"))), parsed.parameters());
	}

	@Test
	public void testTweakLastSignature() {
		// signature (Ljava/util/function/UnaryOperator<Lcom/squareup/javapoet/TypeName;>;)V
		String methodSignature = "(Ljava/util/function/UnaryOperator<Lcom/squareup/javapoet/TypeName;>;)V";
		MethodSignature parsed = Signatures.parseMethodSignature(methodSignature);
		Assertions.assertTrue(parsed.generics().isEmpty());
		Assertions.assertEquals(TypeName.VOID, parsed.result());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.thrown());
		ClassName unaryOperatorClass = ClassName.get(UnaryOperator.class);
		ClassName typeNameClass = ClassName.get(TypeName.class);
		Assertions.assertIterableEquals(Collections.singleton(ParameterizedTypeName.get(unaryOperatorClass, typeNameClass)), parsed.parameters());
	}

	@Test
	public void testTweakLastSignatureVisitor() {
		// signature (Ljava/util/function/UnaryOperator<Lcom/squareup/javapoet/TypeName;>;)V
		String methodSignature = "(Ljava/util/function/UnaryOperator<Lcom/squareup/javapoet/TypeName;>;)V";
		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(TypeAnnotationMapping.EMPTY, s -> false, false);
		new SignatureReader(methodSignature).accept(visitor);
		MethodSignature parsed = visitor.collectMethod();
		Assertions.assertTrue(parsed.generics().isEmpty());
		Assertions.assertEquals(TypeName.VOID, parsed.result());
		Assertions.assertIterableEquals(Collections.emptyList(), parsed.thrown());
		ClassName unaryOperatorClass = ClassName.get(UnaryOperator.class);
		ClassName typeNameClass = ClassName.get(TypeName.class);
		Assertions.assertIterableEquals(Collections.singleton(ParameterizedTypeName.get(unaryOperatorClass, typeNameClass)), parsed.parameters());
	}

	@Test
	public void testCheckHeadSignature() {
		// signature <E:Ljava/lang/Throwable;>(Lnet/fabricmc/filament/mappingpoet/Signatures$HeadChecker<TE;>;)V^TE;
		String raw = "<E:Ljava/lang/Throwable;>(Lnet/fabricmc/filament/mappingpoet/Signatures$HeadChecker<TE;>;)V^TE;";
		MethodSignature parsed = Signatures.parseMethodSignature(raw);
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("E", ClassName.get(Throwable.class))), parsed.generics());
		ClassName headCheckerClass = ClassName.get(Signatures.class).nestedClass("HeadChecker");
		Assertions.assertIterableEquals(Collections.singleton(ParameterizedTypeName.get(headCheckerClass, TypeVariableName.get("E"))), parsed.parameters());
		Assertions.assertEquals(TypeName.VOID, parsed.result());
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("E")), parsed.thrown());
	}

	@Test
	public void testCheckHeadSignatureVisitor() {
		// signature <E:Ljava/lang/Throwable;>(Lnet/fabricmc/filament/mappingpoet/Signatures$HeadChecker<TE;>;)V^TE;
		String raw = "<E:Ljava/lang/Throwable;>(Lnet/fabricmc/filament/mappingpoet/Signatures$HeadChecker<TE;>;)V^TE;";
		PoetClassMethodSignatureVisitor visitor = new PoetClassMethodSignatureVisitor(TypeAnnotationMapping.EMPTY, s -> false, false);
		new SignatureReader(raw).accept(visitor);
		MethodSignature parsed = visitor.collectMethod();
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("E", ClassName.get(Throwable.class))), parsed.generics());
		ClassName headCheckerClass = ClassName.get(Signatures.class).nestedClass("HeadChecker");
		Assertions.assertIterableEquals(Collections.singleton(ParameterizedTypeName.get(headCheckerClass, TypeVariableName.get("E"))), parsed.parameters());
		Assertions.assertEquals(TypeName.VOID, parsed.result());
		Assertions.assertIterableEquals(Collections.singleton(TypeVariableName.get("E")), parsed.thrown());
	}

	@Test
	public void testBrokenVariantSettingSignature() {
		// Ljava/util/Map<Lnet/minecraft/data/client/model/VariantSetting<*>;Lnet/minecraft/data/client/model/VariantSetting<*>.Value;>;
		String signature = "Ljava/util/Map<Lnet/minecraft/data/client/model/VariantSetting<*>;Lnet/minecraft/data/client/model/VariantSetting<*>.Value;>;";
		Map.Entry<Integer, TypeName> parsed = Signatures.parseParameterizedType(signature, 0);
		Assertions.assertEquals(125, parsed.getKey().intValue());
		ClassName variantSettingClass = ClassName.get("net.minecraft.data.client.model", "VariantSetting");
		ParameterizedTypeName wildcardVariantSetting = ParameterizedTypeName.get(variantSettingClass, WildcardTypeName.subtypeOf(TypeName.OBJECT));
		ParameterizedTypeName valueClass = wildcardVariantSetting.nestedClass("Value");
		ClassName mapClass = ClassName.get(Map.class);
		ParameterizedTypeName genericMap = ParameterizedTypeName.get(mapClass, wildcardVariantSetting, valueClass);
		Assertions.assertEquals(genericMap, parsed.getValue());

		PoetTypeSignatureWriter writer = new PoetTypeSignatureWriter(TypeAnnotationBank.EMPTY, s -> false);
		new SignatureReader(signature).acceptType(writer);
		Assertions.assertEquals(genericMap, writer.compute());
	}

	@Test
	public void testStaticOuters() {
		Outer.MiddleStatic.@TestAnno InnerStatic instance = null;

		// https://docs.oracle.com/javase/specs/jvms/se15/html/jvms-4.html#jvms-4.7.20.2-220-B-A.1
		Outer.@TestAnno("a") MiddleStatic<@TestAnno("b") Object>.@TestAnno("c") Inner<@TestAnno("d") Integer> instance2 = new Outer.MiddleStatic<>().new Inner<Integer>();

		String input = "Lnet/fabricmc/mappingpoet/Outer$MiddleStatic<Ljava/lang/Object;>.Inner<Ljava/lang/Integer;>;";

		TypeName name = Signatures.parseFieldSignature(input);
		Assertions.assertEquals("net.fabricmc.mappingpoet.Outer.MiddleStatic<java.lang.Object>.Inner<java.lang.Integer>", name.toString());

		PoetTypeSignatureWriter writer = new PoetTypeSignatureWriter(TypeAnnotationBank.EMPTY, s -> false);
		new SignatureReader(input).acceptType(writer);
		Assertions.assertEquals(name, writer.compute());
	}
}
