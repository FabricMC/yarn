# Naming conventions

## General

Use `UpperCamelCase` for class names. Use `lowerCamelCase` for method names, variable names, and names of fields that are not
both static and final. Use `UPPER_SNAKE_CASE` for names of fields that are both static and final.

Method names should generally be verb phrases (`tick`, `getCarversForStep`), except for "withX", "toX", "fromX", "of" and
builder methods. Class names and non-boolean field and variable names should be noun phrases (`ChunkRegion`, `color`).
Boolean field and variable names should always be adjective phrases or present tense verb phrases (`powered`, `canOpen`),
avoiding the `is` and `has` prefixes when possible (`colored`, not `isColored` or `hasColor`).

To make code as easy to read as possible, keep names in the natural language order. For example, a class representing a chest
block entity should be named `ChestBlockEntity` rather than `BlockEntityChest`. Though prefix naming may be helpful for
grouping classes together in an IDE's tree view, reading and writing code is done much more often than browsing files.

## Class names
Except as noted below, class names should be a singular noun. Interface names should be a noun or an adjective (`Angerable`, `Nameable`).

Classes and interfaces whose purpose is to hold instances or registry keys of `X` as `static final` fields should be named `Xs`. For example, `CatVariants` is an interface holding vanilla `CatVariant`s.
A class whose purpose is to serve as a type of `X` should be named `XType`. Typically, `XType` is pre-defined, while an infinite amount of `X` can be created at runtime or loaded from resource packs. (Compare `EntityType` and `Entity`; note that many existing names violate this rule, such as `StatusEffect` (type) and `StatusEffectInstance` (instance).)

Use `Manager` for a class whose instance manages specific behavior. These are usually attached to a server, client, world, or player.

There are three naming conventions for helper-method classes (i.e. classes that are never instantiated and hold only static members). If the public methods exist for a single purpose, then that purpose, suffixed with "ing", can be used. Examples: `Dismounting`, `SaveLoading`, `BlockLocating`.
When `X` is a name of another class, `Xs` can also be used to hold helper methods related to `X`, so long as it makes sense etymologically and that name has not already been used for holding instances of `X`. Examples: `Uuids`, `Codecs`, `Texts`, `Nullables`.
In other cases, both `Util` and `Helper` suffixes are used.
Try to avoid conflicts with libraries used by Mojang, such as Guava and Apache Commons.

Do not name instantiable classes `Util` or `Helper`.

## Spelling

Use American English for consistency throughout Yarn and with known Mojang names.

If there are two acceptable spellings of the same word, first check if one is already being used in Yarn or by Mojang, and if
not, use the most common spelling.

## Conciseness

Omit words that are made redundant by parameter names or owner class names. For example, use `getChunk(BlockPos pos)` rather
than `getChunkAtPosition(BlockPos pos)` and `Box.create` rather than `Box.createBox`. Don't avoid overloading methods or
shadowing fields.

However, it's more important for a name to be descriptive rather than short, so don't omit important words. When naming
something always look at all its usages, including overriding methods and inheriting classes.

It's important to be concise especially with names used in many places throughout the code, while more obscure names can be
longer and more descriptive.

## Abbreviations

Avoid abbreviations unless it's a common one everyone knows and other yarn names involving the same word use its abbreviated
form. Full names are easier to read quickly and remember ("Which words were abbreviated?") and they often don't take more
time to type thanks to IDE autocompletion. Common abbreviations you should use are:

 - "id" for "identifier"
 - "pos" for "position"
 - "vec" for "vector" (note: use "pos" when it denotes a position)
 - "nbt" for "named binary tag"
 - "buf" for `ByteBuf` (used in networking; use "buffer" for NIO/LWJGL buffers used in client)
 - "init" for "initialize"
 - "min"/"max" for "minimum"/"maximum"
 - Any abbreviations used by Java or libraries ("json", "html", etc.)
 - "o" for the parameter of `equals(Ljava/lang/Object;)Z` methods

Treat acronyms as single words rather than capitalizing every letter. This improves readability (compare `JsonObject` and
`JSONObject`) and it's consistent with Mojang naming (a known name is `NbtIo`).

## Packages

Package names should always be singular to respect Java conventions. Avoid creating new packages directly under `net.minecraft`.

Client-only classes (i.e. those annotated as `@Environment(EnvType.CLIENT)`) must be placed under `net.minecraft.client` (or in some cases `com.mojang.blaze3d` or `net.minecraft.server.integrated`). Classes included in both jars must not be placed under those packages.

## Consistency

Consistency is important as it makes code more readable and names easier to memorize. When possible, use terms that are present
in other Yarn names, in libraries used by Minecraft, or in vanilla strings. The rest of this section lists common names and
name patterns you should use.

### Ticks and updates

Use "tick" for updates done once per tick. Use "update" for other kind of updates.
Note that "tick" is used in block-related code for things that are not performed once per tick for historical reasons. For example, neither `randomTick` nor `scheduledTick` gets called once per tick.

### Value last tick

Use the word "last" for the value that something had last tick (`lastX`, `lastWidth`, etc.).

### Getters, setters, withers, and creators

Use "get" for non-boolean getters and other methods that calculate some property with no side effects other than caching a value
in a private field. For boolean getters, use "is", "can", "should", and similar verbs (like "requires").

Exception: record components should always be prefix-less, even if it means droppng the "get" prefix from the implementing interface.

For more complex computations, "find" and "calc" are frequently used.

Use "set" for methods that set some property. Name the parameter the same as the property (`setColor(color)`, not
`setColor(newColor)`).

Use "with" for methods that return a copy of an object with a different value for some property. Name the parameter the same
as the property.

Use "create" for methods that create a new instance of some object. Use "get or create" for methods that create a new
instance only if one does not already exist. Don't use "get or create" for lazy initialization, though.
For collection-like classes and other classes that hold contents, `of` is preferred over `create`. `of` methods can return a reused value (like with `List.of()`).

### Serialization

Use "serializer" for objects whose purpose is serializing or deserializing some type of object (`RecipeSerializer`). Use
"serialize" and "deserialize" for methods only when serializing or deserializing an object other than the one the method is in.

Use "from" for static methods that create an object of the method owner's type (`fromJson`, `fromNbt`, `fromString`). Use "to"
for methods that convert an object to another type (`toString`, `toLong`, `toNbt`).

Use "read" for non-static methods that load data into the object. Use "write" for methods that save data to an *existing* 
object passed as a parameter.

### Factories and builders

Use "factory" for objects whose purpose is creating other objects.

Use "builder" for objects whose purpose is helping with the creation of an immutable object. Name builder fields, methods, and params the same as the field they're setting, without any prefix.
If a builder method adds to a list or puts to a map, then it should be named as the singular version of the field name. For example, a builder method that adds a value to `effects` list should be named `effect`.
Name the object-creating method "build". Static methods that returns a builder should be named "builder".

### Collections

Use a plural name for collections and maps rather than the words "list", "set", "array", etc., unless it's a collection of
collections or there are several collections of different types containing the same objects (`entities`, `entityLists`).

When it's enough, name maps based on the value type. Otherwise, name it in the "`keyToValue`" or "`valuesByKeys`" format.

### Coordinates

Coordinates can be named `x`, `y`, and `z` when it's clear what they represent. If clarification is needed, add a word in
front of the coordinate (`velocityX`, not `xVelocity`).

Refer to screen coordinates as `x` and `y` when inside rendering code such as `DrawContext`.
This also applies to positions of screen widgets if they are specified using the topleft corner position, the height, and the width. If instead the positions of widgets are specified using 2 coordinates, use `left`, `top`, `right`, and `bottom` instead of `x1`, `y1`, `x2`, and `y2`.

### Worlds
Use "world" to describe the world with blocks and entities. Use "dimension" to describe the types of worlds that can be defined by data packs.

There is no single convention on what to call the world save (a save data that contains multiple worlds, like the Overworld, the Nether, and the End). In many places this is referred to as "level", especially in `level.dat`-related code. (Examples: `LevelLoadingScreen`, `LevelProperties`, `LevelStorage`)
In client GUI-related code, however, "world" is also used. (Examples: `CreateWorldScreen`, `SelectWorldScreen`, `WorldCreationSettings`)
Server-side code generally refers to them as "save". (Examples: `SaveLoading`)

### Special classes

Unless a more descriptive name is better, these Minecraft classes should get specific names as fields and parameters:

| Class                                                  | Name              |
| ------------------------------------------------------ | ----------------- |
| `net.minecraft.client.render.VertexConsumerProvider`   | `vertexConsumers` |
| `net.minecraft.client.util.math.MatrixStack`           | `matrices`        |
| `net.minecraft.registry.RegistryWrapper.WrapperLookup` | `registries`      |
| Interfaces implemented by `World`                      | `world`           |

### Other conventions
- Methods that register the values and return the default value (i.e. `Registries.Initializer`) should be named `registerAndGetDefault` for static registries, and `bootstrap` for dynamic registries.
- Use "draw" for drawing a simple shape, such as rectangles, cubes, texts, and a texture. Use "render" for drawing something more concrete, such as the sky, blocks, entities, particles, user interfaces, and buttons. A "render" method typically includes multiple "draw" calls.
- Use `index` for numeric IDs assigned to `Enum`s.
- Name methods `tryX` if it performs X conditionally. For example, a method that checks a spawn condition and only spawns an entity if it is satisfied should be named `trySpawn` (not `trySpawning`).

## Javadocs

Write sentences for class, method and field javadocs, starting with an uppercase and ending with a period. Start method docs with verbs, like `Sets` or `Called`. Use HTML tags such as `<p>` if the docs have several paragraphs, as line wraps are converted to spaces in the generated documentation. Feel free to start a new line whenever you feel the current line is too long. Note that some ending tags such as `</p>` are not mandatory for Javadocs to render correctly.

Parameter and `@return` documentation should use quick descriptions without initial capitalization or punctuation, such as `{@code true} if the block placement was successful, {@code false} otherwise`. `{@return}` used in the first sentence can duplicate enclosed text to the return description.

Use `{@index}` to allow enclosed text to be indexed by the Javadoc search.

Avoid using the `@param` tag for documentation of methods. Add parameter documentations to the parameter itself instead of adding `@param` tags to its owner method, so Matcher can update them properly across Minecraft updates. However, you can use the tag for type parameters (such as `<T>` in `public class Lazy<T>`), which cannot be documented separately.

Javadoc will take the first sentence, ended by the first `.`, as a brief description of the member you are documenting. Note that `.` from abbreviations, such as `i.e.`, count.

### Packages

Since enigma format does not support `package-info.java` file creation, yarn keeps these files in `src/packageDocs/java` to supply javadocs for packages. Their only purpose is to host Javadoc for yarn packages, which are currently not exported to mappings, and their Javadocs should follow the conventions just like enigma-based Javadocs.

### Tooling

Fabric-hosted Javadocs are generated using [JDK 21 Standard Doclet](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html) and can use any feature it supports. For example, it has a [list of supported tags](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html#where-tags-can-be-used). You can personally build the documentation with a newer Java version. See [the 'Checking Javadoc' section](#checking-javadoc) for how to build the documentation locally.

### Custom tags

A few additional block tags are supported:

 - `@apiNote`: API Notes. A few comments for users of the documented API.
 - `@implSpec`: Implementation Specification. Tells how this method is implemented; usually avoided as yarn doesn't define implementations.
 - `@implNote`: Implementation Notes. A few comments on the implementation.

Feel free to use these tags and write under these sections.

### Linking to other members

Use `@link`, `@linkplain` and `@see` tags to refer to other parts of the code.

You can use the simple name of a class when its import is assumed to exist.

A class is assumed to be imported in the following scenarios:

- If it is from the `java.lang` package
- If it is from the same package as the currently documented class
- If it is used as part of its API, such as in the signature of the class or its members (methods and fields). See Javadoc's definition of "use" in its [`-use` command line option specification](https://docs.oracle.com/en/java/javase/16/docs/specs/man/javadoc.html#options-for-javadoc).

If it does not fulfill one of these scenarios, use the [full binary name](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/ClassLoader.html#binary-name), such as `com.google.common.collect.Lists` rather than simply `Lists`. Unlike class naming in enigma, do not use `/` to separate packages; use `.` instead.

Use Yarn mappings when referencing Minecraft members, such as `net.minecraft.server.world.ThreadedAnvilChunkStorage` rather than `net.minecraft.class_3898`. The Javadoc task will warn if some links no longer work after a rename.

<details>
<summary>
An example
</summary>

Assume this is the decompiled content in Enigma, which does not show imports:

```java
/**
 * Assume this class is from the {@code net.example.stuff} package.
 *
 * <p>You can link to {@link Optional} as it's part of the class signature (type parameter bound).
 *
 * <p>You must fully qualify {@link net.example.stuff.basic.BasicStuffUser} when linking as it is not in
 * any signature and is from a different package.
 */
public class Stuff<T extends Optional<?>> {
	/**
	 * You can link to {@link Listener} with the simple name as it's part of a field's signature.
	 */
	protected Listener listener;

	/**
	 * You can link to {@link List} with the simple name as it's part of a method's signature.
	 *
	 * <p>You must fully qualify {@link net.example.util.UtilityClass} when linking because it is not part
	 * of any signature (even though it is used in code) and is from a different package.
	 */
	public Stuff(List<Integer> opt) {
		UtilityClass.callMethod(opt);
	}
}
```
</details>

### Checking Javadoc

After writing a Javadoc, you should check its validity. This can easily be done from GitHub using the 'Checks' section, or by clicking the check icon after each commit. For any JDK version, the section titled `Run ./gradlew build javadocJar checkMappings mapNamedJar --stacktrace` will contain output related to Javadoc generation in the `Task :javadoc` section.

You can also check Javadoc validity locally using the `./gradlew javadoc` (Linux, macOS) or `gradlew javadoc` (Windows) commands; this will take a while given the sheer size of the Minecraft codebase. After that, you can enter the `build/docs/javadoc` directory to obtain the generated Javadoc and ensure it renders correctly.

If you are unsure if your Javadoc is correct stylistically, we recommend you to run the `javadoc` task and check its output, as described in the previous paragraph.

### Game content capitalization

When describing content in the game itself, do not use capitalization. For example, the following is incorrect:

> The quick Snow Fox jumped over the tamed Wolf.

The following is acceptable:

> The quick snow fox jumped over the tamed wolf.

In order to hint that the description references the game's content, inline links may be used.

## Mojang names

**Do not use names from Mojang's obfuscation maps.**

Use names that match names in strings in the vanilla code, unless that string is outdated or inaccurate. This avoids confusion,
especially from new modders who may not understand what a class exception message is referring to.

Even if a known Mojang name doesn't appear in any strings, it's a good idea to use it since the official name is a good
indicator of the class's actual purpose and makes it less likely the name will have to be changed in the future. For
example, don't name a class that Mojang calls `BedrockBlock` `NoSpawningBlock`, even if its only purpose is to disable mob
spawning, because Mojang may decide to override more methods in that class, breaking mods that were using it in an unexpected
way.

There are however three exceptions to this rule:
 - Use "world" for what Mojang calls "level" (see https://github.com/FabricMC/yarn/issues/89)
 - Use "screen handler" or "handler" (depending on context; if the screen part is obvious it can be omitted) for what Mojang calls "menu" (see https://github.com/FabricMC/yarn/pull/1106)
 - Use "inventory" for what Mojang calls "container" (no issue yet)

## Things to avoid

Don't name methods based on implementation details. Names should describe what methods do, not how they work.

Avoid including Java-related information in names. For example, don't prefix class names with `I`, or `Enum` and
don't prefix methods with `private`. Instead, try to find meaningful names to describe differences between classes. In the
case of abstract classes, this may involve renaming subclasses to more specific names.
