/*
 * This file is free for everyone to use under the Creative Commons Zero license.
 */

/**
 * The Named Binary Tag (NBT) data format.
 *
 * <p>NBT is a simple data structure format used in Minecraft's in-game data
 * serialization. Data stored on disks, modified by commands, and transported
 * via packets are usually in this format. In most cases, NBT is <strong>not
 * the runtime form of data</strong>; data are instead written to Java
 * class fields using Java types, and during serialization (as part of saving
 * or network transportation) or modification by commands, the code will write
 * the fields to NBT data. NBT data is also known as "NBT element".
 *
 * <h2 id=types>Types</h2>
 * <p>There are 13 NBT element types, as shown below. Each NBT type has its own class,
 * and a {@link NbtType} instance used during deserialization in the {@code TYPE}
 * static field. They also have the "numeric ID" used during serialization and at
 * {@link NbtCompound#getList(String, int)} method. The ID of the element's type can
 * be obtained via the {@link NbtElement#getType()} method. There is also a special
 * ID, {@link NbtElement#NUMBER_TYPE}, which indicates the <strong>read value</strong>
 * can be any of the six numeric types; this cannot be used as the list's held type.
 *
 * <table>
 * <caption>NBT types</caption>
 * <thead>
 *   <tr>
 *     <th>Class</th>
 *     <th>Numeric ID</th>
 *     <th>Immutable?</th>
 *     <th>Default</th>
 *     <th>Description</th>
 *   </tr>
 * </thead>
 * <tbody>
 *   <tr>
 *     <td>{@link NbtEnd}<br></td>
 *     <td>{@value NbtElement#END_TYPE}</td>
 *     <td>Yes</td>
 *     <td>(none)</td>
 *     <td>Internal type used during serialization, should not be used directly</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtByte}</td>
 *     <td>{@value NbtElement#BYTE_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0}</td>
 *     <td>Corresponds to {@code byte}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtShort}</td>
 *     <td>{@value NbtElement#SHORT_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0}</td>
 *     <td>Corresponds to {@code short}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtInt}</td>
 *     <td>{@value NbtElement#INT_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0}</td>
 *     <td>Corresponds to {@code int}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtLong}</td>
 *     <td>{@value NbtElement#LONG_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0L}</td>
 *     <td>Corresponds to {@code long}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtFloat}</td>
 *     <td>{@value NbtElement#FLOAT_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0.0f}</td>
 *     <td>Corresponds to {@code float}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtDouble}</td>
 *     <td>{@value NbtElement#DOUBLE_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code 0.0}</td>
 *     <td>Corresponds to {@code double}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtByteArray}</td>
 *     <td>{@value NbtElement#BYTE_ARRAY_TYPE}</td>
 *     <td>No</td>
 *     <td>Empty array</td>
 *     <td>Corresponds to {@code byte[]}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtString}</td>
 *     <td>{@value NbtElement#STRING_TYPE}</td>
 *     <td>Yes</td>
 *     <td>{@code ""}</td>
 *     <td>Corresponds to {@link String}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtList}</td>
 *     <td>{@value NbtElement#LIST_TYPE}</td>
 *     <td>No</td>
 *     <td>Empty list</td>
 *     <td>List of NBT elements with the same type</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtCompound}</td>
 *     <td>{@value NbtElement#COMPOUND_TYPE}</td>
 *     <td>No</td>
 *     <td>Empty compound</td>
 *     <td>Hash map-like object with string keys that can store any NBT elements</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtIntArray}<br></td>
 *     <td>{@value NbtElement#INT_ARRAY_TYPE}</td>
 *     <td>No</td>
 *     <td>Empty array</td>
 *     <td>Corresponds to {@code int[]}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link NbtLongArray}</td>
 *     <td>{@value NbtElement#LONG_ARRAY_TYPE}</td>
 *     <td>No</td>
 *     <td>Empty array</td>
 *     <td>Corresponds to {@code long[]}</td>
 *   </tr>
 * </tbody>
 * </table>
 *
 * <h3 id=nbt-end>{@link NbtEnd}</h3>
 * <p>NbtEnd is a special sentinel used to indicate the end of lists and compounds.
 * This is also used as the type of empty lists.
 *
 * <h3 id=numeric-types>Numeric types</h3>
 * <p>NBT offers 6 numeric types (4 for integers and 2 for decimals), all corresponding
 * to Java equivalents. There is no unsigned type or {@code char} equivalent. These
 * inherit {@link AbstractNbtNumber}. To create an instance of these, use the {@code of}
 * static method, and to obtain the value as the Java primitive value, use the
 * {@code typeValue()} method.
 * 
 * <pre>{@code
 *   NbtInt nbt = NbtInt.of(100);
 *   int value = nbt.intValue();
 * }</pre>
 *
 * <p>One thing to note here is that NBT lacks the boolean type; instead {@link NbtByte}
 * is used when boolean values are needed. See also {@link NbtByte#of(boolean)} method.
 *
 * <h3 id=nbt-typed-array>NBT typed arrays</h3>
 * <p>There are 3 typed arrays in NBT: {@link NbtByteArray}, {@link NbtIntArray}, and
 * {@link NbtLongArray}. There are no array types for shorts, floats or doubles. While they
 * are arrays internally, they also support {@linkplain AbstractNbtList#add adding items}
 * at runtime. Like Java arrays, out of bounds access is forbidden and will throw
 * {@link ArrayIndexOutOfBoundsException}.
 *
 * <h3 id=nbt-list>{@link NbtList}</h3>
 * <p>NbtList is a list of a specific type of NBT elements. Empty lists always have the type
 * set as {@link NbtEnd}. {@link NbtList#add} throws when the type is incompatible, while
 * {@link NbtList#addElement} does nothing and returns {@code false} in that case.
 * There are type-specific getters which can be used to get the value. They return the default
 * value when type mismatch occurs or when the index is out of bounds. Note that they do not
 * cast the value at all.
 *
 * <pre>{@code
 *   NbtList list = new NbtList();
 *   list.addElement(0, NbtFloat.of(0.5f));
 *   float firstItem = list.getFloat(0); // 0.5f
 *   float secondItem = list.getFloat(1); // 0.0f (default value)
 *   double firstItemDouble = list.getDouble(0); // 0.0 (no casting)
 *   list.addElement(1, NbtDouble.of(1.0)); // returns false, does nothing
 *   list.add(NbtDouble.of(2.0)); // throws UnsupportedOperationException
 * }</pre>
 *
 * <h3 id=nbt-compound>{@link NbtCompound}</h3>
 * <p>NbtCompound is a hash map-like key-value storage object. It also acts as the root
 * object for serialized NBTs. The keys are always strings, while the values can be of
 * any type, and multiple value types can be mixed in a single compound. The order of items
 * is not guaranteed. There are generic {@link NbtCompound#put} and {@link NbtCompound#get}
 * methods, and there also exist type-specific getters and putters. Like the list getters
 * mentioned above, they return the default value when the key is missing or type mismatch
 * occurs. However, unlike lists, they attempt to cast numeric values.
 *
 * <pre>{@code
 *   NbtCompound compound = new NbtCompound();
 *   compound.put("Awesomeness", NbtInt.of(90));
 *
 *   // Don't do this! This will crash if the type is incompatible,
 *   // even if they can be casted!
 *   NbtInt awesomenessNbt = (NbtInt)compound.get("Awesomeness");
 *
 *   compound.putLong("Awesomeness", 100L);
 *   int awesomeness = compound.getInt("Awesomeness"); // 100 (after casting)
 *   int evilness = compound.getInt("Evilness"); // 0 (default value)
 *
 *   // Shortcuts for getting and putting a boolean value
 *   // (internally represented as a byte)
 *   compound.putBoolean("Awesome", true);
 *   boolean awesome = compound.getBoolean("Awesome");
 *   compound.put("awesome", NbtDouble.of(1.0)); // key is case-sensitive
 * }</pre>
 *
 * <h2 id=using-nbt>Using NBT</h2>
 * <p>As noted before, NBT is a <strong>serialized format, not runtime format</strong>.
 * Unrecognized custom data stored inside NBT will be lost once the game loads it and saves.
 * Therefore, modifying NBT is usually not the solution for attaching custom data to
 * objects like entities. For this purpose it is recommended to use third-party APIs
 * or use Mixin to add a field to the class.
 *
 * <p>With that said, here is how to use NBT in the vanilla code:
 *
 * <p>Methods named {@code readNbt} will read the values from the element, modifying
 * the current instance. Static methods named {@code fromNbt} will create a new instance
 * of the class with values from the element. Methods named {@code writeNbt} will add
 * the NBT data to the passed element. Methods named {@code toNbt} will create and return
 * a new element with the data.
 *
 * <p>There are several helper classes for NBTs:
 * <ul>
 * <li>{@link NbtHelper} contains methods for reading and writing game profiles, block
 * states, UUIDs, and block positions. It also contains a method for comparing elements.</li>
 * <li>{@link NbtIo} contains methods for reading and writing NBT compounds on a file.</li>
 * <li>{@link NbtOps} is the class defining interactions with the DataFixerUpper library.
 * The library abstracts operations between data formats (such as JSON or NBT) using "ops".
 * Use {@link NbtOps#INSTANCE} to decode the NBT data using DataFixerUpper.</li>
 * <li>{@link StringNbtReader} reads the stringified NBT (SNBT) and converts to NBT.</li>
 * <li>{@link net.minecraft.nbt.visitor.NbtElementVisitor} and its subclasses allow converting
 * NBT elements to string or {@link net.minecraft.text.Text}.</li>
 * <li>{@link net.minecraft.nbt.scanner.NbtScanner} scans the NBT data without reading the
 * whole data at once, allowing efficient decoding when only parts of the data are needed.</li>
 * </ul>
 *
 * <p>NBT compounds can also be transported using {@link
 * net.minecraft.network.PacketByteBuf#writeNbt} and {@link
 * net.minecraft.network.PacketByteBuf#readNbt}.
 */

package net.minecraft.nbt;
