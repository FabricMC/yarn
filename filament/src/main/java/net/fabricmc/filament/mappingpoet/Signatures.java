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

package net.fabricmc.filament.mappingpoet;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import net.fabricmc.filament.mappingpoet.signature.ClassSignature;
import net.fabricmc.filament.mappingpoet.signature.MethodSignature;

public final class Signatures {
	public static ClassSignature parseClassSignature(final String signature) {
		// <A:Labc.Def:Ljava.util.Iterable<Ljava/lang.Object;>;B:Ljava/lang/Object>Ljava/lang/Object; etc etc
		int index = 0;
		char ch;
		List<TypeVariableName> generics = Collections.emptyList();

		if (signature.charAt(0) == '<') {
			// parse generic decl
			index++; // consume '<'

			// parse type params e.g. <A, B>
			generics = new LinkedList<>();

			while ((ch = signature.charAt(index)) != '>') {
				int genericNameStart = index;

				if (ch == ':') {
					throw errorAt(signature, index);
				}

				do {
					index++;
				} while (signature.charAt(index) != ':');

				String genericName = signature.substring(genericNameStart, index);

				List<TypeName> bounds = new LinkedList<>();
				boolean classBound = true;

				while (signature.charAt(index) == ':') {
					// parse bounds
					index++; // consume ':'

					if (classBound && signature.charAt(index) == ':') {
						// No class bound, only interface bounds, so '::'
						classBound = false;
						continue;
					}

					classBound = false;
					Map.Entry<Integer, TypeName> bound = parseParameterizedType(signature, index);
					index = bound.getKey();
					bounds.add(bound.getValue());
				}

				generics.add(TypeVariableName.get(genericName, bounds.toArray(new TypeName[0])));
			}

			index++; // consume '>'
		}

		LinkedList<TypeName> supers = new LinkedList<>();

		while (index < signature.length()) {
			Map.Entry<Integer, TypeName> bound = parseParameterizedType(signature, index);
			index = bound.getKey();
			supers.add(bound.getValue());
		}

		return new ClassSignature(generics, supers.removeFirst(), supers);
	}

	public static MethodSignature parseMethodSignature(String signature) {
		int index = 0;
		char ch;
		List<TypeVariableName> generics = Collections.emptyList();

		if (signature.charAt(0) == '<') {
			// parse generic decl
			index++; // consume '<'

			// parse type params e.g. <A, B>
			generics = new LinkedList<>();

			while ((ch = signature.charAt(index)) != '>') {
				int genericNameStart = index;

				if (ch == ':') {
					throw errorAt(signature, index);
				}

				do {
					index++;
				} while (signature.charAt(index) != ':');

				String genericName = signature.substring(genericNameStart, index);

				List<TypeName> bounds = new LinkedList<>();
				boolean classBound = true;

				while (signature.charAt(index) == ':') {
					// parse bounds
					index++; // consume ':'

					if (classBound && signature.charAt(index) == ':') {
						// No class bound, only interface bounds, so '::'
						classBound = false;
						continue;
					}

					classBound = false;
					Map.Entry<Integer, TypeName> bound = parseParameterizedType(signature, index);
					index = bound.getKey();
					bounds.add(bound.getValue());
				}

				generics.add(TypeVariableName.get(genericName, bounds.toArray(new TypeName[0])));
			}

			index++; // consume '>'
		}

		if (signature.charAt(index) != '(') {
			throw errorAt(signature, index);
		}

		index++; // consume '('

		LinkedList<TypeName> params = new LinkedList<>();

		while (signature.charAt(index) != ')') {
			Map.Entry<Integer, TypeName> param = parseParameterizedType(signature, index);
			index = param.getKey();
			params.add(param.getValue());
		}

		index++; // consume ')'

		TypeName returnType;

		if (signature.charAt(index) == 'V') {
			returnType = TypeName.VOID;
			index++;
		} else {
			Map.Entry<Integer, TypeName> parsedReturnType = parseParameterizedType(signature, index);
			index = parsedReturnType.getKey();
			returnType = parsedReturnType.getValue();
		}

		LinkedList<TypeName> thrown = new LinkedList<>();

		while (index < signature.length() && signature.charAt(index) == '^') {
			index++; // consume '^'
			Map.Entry<Integer, TypeName> parsedThrown = parseParameterizedType(signature, index);
			index = parsedThrown.getKey();
			thrown.addLast(parsedThrown.getValue());
		}

		return new MethodSignature(generics, params, returnType, thrown);
	}

	public static TypeName parseFieldSignature(String signature) {
		return parseParameterizedType(signature, 0).getValue();
	}

	public static Map.Entry<Integer, TypeName> parseParameterizedType(final String signature, final int startOffset) {
		GenericStack stack = new GenericStack();

		int index = startOffset;

		// the loop parses a type and try to quit levels if possible
		do {
			char ch = signature.charAt(index);
			boolean parseExactType = true;
			boolean bounded = false;
			boolean extendsBound = false;

			switch (ch) {
			case '*': {
				index++;
				parseExactType = false;
				stack.addWildcard();
				break;
			}
			case '+': {
				index++;
				bounded = true;
				extendsBound = true;
				break;
			}
			case '-': {
				index++;
				bounded = true;
				extendsBound = false;
				break;
			}
			default: {
				// do nothing
			}
			}

			if (parseExactType) {
				int arrayLevel = 0;

				while ((ch = signature.charAt(index)) == '[') {
					index++;
					arrayLevel++;
				}

				index++; // whatever the prefix is it's consumed
				switch (ch) {
				case 'B':
				case 'C':
				case 'D':
				case 'F':
				case 'I':
				case 'J':
				case 'S':
				case 'Z': {
					// primitives
					stack.add(getPrimitive(ch), arrayLevel, bounded, extendsBound);
					break;
				}
				case 'T': {
					// "TE;" for <E>
					int nameStart = index;

					while (signature.charAt(index) != ';') {
						index++;
					}

					String typeVarName = signature.substring(nameStart, index);
					stack.add(TypeVariableName.get(typeVarName), arrayLevel, bounded, extendsBound);
					index++; // read ending ";"
					break;
				}
				case 'L': {
					// Lcom/example/Outer<TA;TB;>.Inner<TC;>;
					// Lcom/example/Outer$Inner<TA;>;
					// dot only appears after ">"!
					int nameStart = index;
					ClassName currentClass = null;
					int nextSimpleNamePrev = -1;

					do {
						ch = signature.charAt(index);

						if (ch == '/') {
							if (currentClass != null) {
								throw errorAt(signature, index);
							}

							nextSimpleNamePrev = index;
						}

						if (ch == '$' || ch == '<' || ch == ';') {
							if (currentClass == null) {
								String packageName = nextSimpleNamePrev == -1 ? "" : signature.substring(nameStart, nextSimpleNamePrev).replace('/', '.');
								String simpleName = signature.substring(nextSimpleNamePrev + 1, index);
								currentClass = ClassName.get(packageName, simpleName);
							} else {
								String simpleName = signature.substring(nextSimpleNamePrev + 1, index);
								currentClass = currentClass.nestedClass(simpleName);
							}

							nextSimpleNamePrev = index;
						}

						index++;
					} while (ch != '<' && ch != ';');

					assert currentClass != null;

					if (ch == ';') {
						stack.add(currentClass, arrayLevel, bounded, extendsBound);
					}

					if (ch == '<') {
						stack.push(Frame.ofClass(currentClass), arrayLevel, bounded, extendsBound);
					}

					break;
				}
				default: {
					throw errorAt(signature, index);
				}
				}
			}

			// quit generics
			quitLoop:

			while (stack.canQuit() && signature.charAt(index) == '>') {
				// pop
				stack.popFrame();
				index++;

				// followups like .B<E> in A<T>.B<E>
				if ((ch = signature.charAt(index)) != ';') {
					if (ch != '.') {
						throw errorAt(signature, index);
					}

					index++;
					int innerNameStart = index;
					final int checkIndex = index;
					stack.checkHead(head -> {
						if (!(head instanceof ParameterizedTypeName)) {
							throw errorAt(signature, checkIndex);
						}
					});

					while (true) {
						ch = signature.charAt(index);

						if (ch == '.' || ch == ';' || ch == '<') {
							String simpleName = signature.substring(innerNameStart, index);

							if (ch == '.' || ch == ';') {
								stack.tweakLast(name -> ((ParameterizedTypeName) name).nestedClass(simpleName));

								if (ch == ';') {
									index++;
									break;
								}
							} else {
								stack.push(Frame.ofGenericInnerClass((ParameterizedTypeName) stack.deque.getLast().typeNames.removeLast(), simpleName));
								index++;
								break quitLoop;
							}
						}

						index++;
					}
				} else {
					index++;
				}
			}
		} while (stack.canQuit());

		assert stack.deque.size() == 1;
		assert stack.deque.getLast().typeNames.size() == 1;
		return new AbstractMap.SimpleImmutableEntry<>(index, stack.collectFrame());
	}

	private static IllegalArgumentException errorAt(String signature, int index) {
		return new IllegalArgumentException(String.format("Signature format error at %d for \"%s\"", index, signature));
	}

	public static TypeName wrap(TypeName component, int level, boolean bounded, boolean extendsBound) {
		TypeName ret = component;

		for (int i = 0; i < level; i++) {
			ret = ArrayTypeName.of(ret);
		}

		return bounded ? extendsBound ? WildcardTypeName.subtypeOf(ret) : WildcardTypeName.supertypeOf(ret) : ret;
	}

	public static TypeName getPrimitive(char c) {
		switch (c) {
		case 'B':
			return TypeName.BYTE;
		case 'C':
			return TypeName.CHAR;
		case 'D':
			return TypeName.DOUBLE;
		case 'F':
			return TypeName.FLOAT;
		case 'I':
			return TypeName.INT;
		case 'J':
			return TypeName.LONG;
		case 'S':
			return TypeName.SHORT;
		case 'V':
			return TypeName.VOID;
		case 'Z':
			return TypeName.BOOLEAN;
		}

		throw new IllegalArgumentException("Invalid primitive " + c);
	}

	@FunctionalInterface
	interface Frame<T extends TypeName> {
		static Frame<ParameterizedTypeName> ofClass(ClassName className) {
			return parameters -> ParameterizedTypeName.get(className, parameters.toArray(new TypeName[0]));
		}

		static Frame<ParameterizedTypeName> ofGenericInnerClass(ParameterizedTypeName outerClass, String innerName) {
			return parameters -> outerClass.nestedClass(innerName, parameters);
		}

		static Frame<TypeName> collecting() {
			return parameters -> {
				if (parameters.size() != 1) {
					throw new IllegalStateException();
				}

				return parameters.get(0);
			};
		}

		T acceptParameters(List<TypeName> parameters);
	}

	@FunctionalInterface
	interface HeadChecker<E extends Throwable> {
		void check(TypeName typeName) throws E;
	}

	static final class GenericStack {
		private final Deque<Element> deque = new LinkedList<>();

		GenericStack() {
			deque.addLast(new Element(Frame.collecting()));
		}

		public boolean canQuit() {
			return deque.size() > 1;
		}

		public void push(Frame<?> frame) {
			deque.addLast(new Element(frame));
		}

		public void push(Frame<?> frame, int arrayLevel, boolean bounded, boolean extendsBound) {
			deque.getLast().pushAttributes(arrayLevel, bounded, extendsBound);
			push(frame);
		}

		public void addWildcard() {
			add(WildcardTypeName.subtypeOf(ClassName.OBJECT), 0, false, false);
		}

		public void add(TypeName typeName, int arrayLevel, boolean bounded, boolean extendsBound) {
			deque.getLast().add(typeName, arrayLevel, bounded, extendsBound);
		}

		public void tweakLast(UnaryOperator<TypeName> modifier) {
			LinkedList<TypeName> typeNames = deque.getLast().typeNames;
			typeNames.addLast(modifier.apply(typeNames.removeLast()));
		}

		public TypeName collectFrame() {
			return deque.removeLast().pop();
		}

		public void popFrame() {
			TypeName name = collectFrame();
			deque.getLast().typeNames.addLast(name);
		}

		public <E extends Throwable> void checkHead(HeadChecker<E> checker) throws E {
			checker.check(deque.getLast().typeNames.getLast());
		}

		private static final class Element {
			final LinkedList<TypeName> typeNames;
			final Frame<?> frame;
			int arrayLevel = 0;
			boolean bounded = false;
			boolean extendsBound = false;

			Element(Frame<?> frame) {
				this.typeNames = new LinkedList<>();
				this.frame = frame;
			}

			void add(TypeName typeName, int arrayLevel, boolean bounded, boolean extendsBound) {
				pushAttributes(arrayLevel, bounded, extendsBound);
				typeNames.addLast(typeName);
			}

			private void updateLast() {
				if (!typeNames.isEmpty()) {
					TypeName lastTypeName = typeNames.removeLast();
					typeNames.addLast(Signatures.wrap(lastTypeName, this.arrayLevel, this.bounded, this.extendsBound));
				}
			}

			void pushAttributes(int arrayLevel, boolean bounded, boolean extendsBound) {
				updateLast();
				this.arrayLevel = arrayLevel;
				this.bounded = bounded;
				this.extendsBound = extendsBound;
			}

			TypeName pop() {
				updateLast();
				return frame.acceptParameters(typeNames);
			}
		}
	}
}
