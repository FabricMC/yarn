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

import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

enum StupidEnum {
	FIRST,
	@TestAnno
	SECOND;
}

// signature <A::Ljava/lang/Comparable<-TA;>;>Ljava/lang/Object;
public class TestOuter<A extends Comparable<? super A>> {
	// signature <B::Ljava/util/Comparator<-TA;>;C:Ljava/lang/ClassLoader;:Ljava/lang/Iterable<*>;>Ljava/lang/Object;
	class Inner<B extends Comparator<? super A>, C extends ClassLoader & AutoCloseable> {
		// signature <D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;
		class ExtraInner<D extends UnaryOperator<Map<int[][], BiFunction<B, A, C>>>> {
			ExtraInner(Inner<B, C> Inner.this) {
				// constructor receiver example. Notice 'this' cannot receive parameter annos
			}

			void work(@TestAnno("on extra inner")ExtraInner<D> this, Inner<@TestAnno("pig") B, C> @TestAnno("lion") [][] @TestAnno("rat") [] arr) {
			}

			void work2(ExtraInner<D> this) {
			}
		}
	}
}

class Outer {
	void eat(@BorkAnno @TestAnno MiddleTwo.InnerThree<Integer> apple) {
	}

	void eat(@BorkAnno MiddleStatic.@TestAnno InnerStatic apple) {
	}

	static class MiddleStatic<T> {
		static class InnerStatic {
		}

		class Inner<U> {
		}
	}

	class MiddleTwo {
		class InnerThree<E> {
		}
	}
}

class OuterTwo<T> {
	static class InnerOne<G> {
		class InnerTwo {
			InnerTwo(InnerOne<G> InnerOne.this) {
			}

			void called(InnerTwo this, InnerTwo other) {
			}

			class NestedThree<W> {
				void work(NestedThree<W> this, NestedThree<W> other) {
				}
			}
		}
	}

	class InnerTwo {
		class NestedThree<W> {
			void workSelf(@TestAnno("on myself!")NestedThree<@TestAnno("on my W") W> this) {
			}

			void workSelf1(NestedThree<@TestAnno("on my W only") W> this) {
			}

			void workSelf2(@TestAnno("on myself only")NestedThree<W> this) {
			}

			void work(@TestAnno("on myself!")NestedThree<@TestAnno("on my W") W> this, NestedThree<@TestAnno("on their W") W> other) {
			}
		}
	}
}
