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

/**
 * A context to retrieve if a class is an instance inner class. Useful for
 * placing type annotations correctly. See
 * <a href="https://docs.oracle.com/javase/specs/jvms/se15/html/jvms-4.html#d5e9440">
 * an example in JVM Specification 15.</a>
 */
public interface ClassStaticContext {
	/**
	 * Returns if this class is an instance inner class.
	 *
	 * <p>For example, a top-level class is not so. A static nested
	 * class, such as {@code Map.Entry}, is not as well.</p>
	 *
	 * @param internalName the JVM name of the class
	 * @return whether this class is not an instance inner class.
	 */
	boolean isInstanceInner(String internalName);
}
