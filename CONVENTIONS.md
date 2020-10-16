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

## Spelling

Use American English for consistency throughout Yarn and with known Mojang names.

If there are two accepted spellings of the same word, first check if one is already being used in Yarn or by Mojang, and if
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
 - "nbt" for "named binary tag"
 - "init" for "initialize"
 - "min"/"max" for "minimum"/"maximum"
 - Any abbreviations used by Java or libraries ("json", "html", etc.)

Treat acronyms as single words rather than capitalising every letter. This improves readability (compare `JsonObject` and
`JSONObject`) and it's consistent with Mojang naming (a known name is `NbtIo`).

## Packages

Package names should always be singular to respect Java conventions. Try to respect the Mojang package structure to avoid
visibility problems in the future.

## Consistency

Consistency is important as it makes code more readable and names easier to memorize. When possible, use terms that are present
in other Yarn names, in libraries used by Minecraft, or in vanilla strings. The rest of this section lists common names and
name patterns you should use.

### Ticks and updates

Use "tick" for updates done once per tick. Use "update" for other kind of updates.

### Value last tick

Use the word "last" for the value that something had last tick (`lastX`, `lastWidth`, etc.).

### Getters, setters, withers, and creators

Use "get" for non-boolean getters and other methods that calculate some property with no side effects other than caching a value
in a private field. For boolean getters, use "is".

Use "set" for methods that set some property. Name the parameter the same as the property (`setColor(color)`, not
`setColor(newColor)`).

Use "with" for methods that return a copy of an object with a different value for some property. Name the parameter the same
as the property.

Use "create" for methods that create a new instance of some object. Use "get or create" for methods that create a new
instance only if one does not already exist. Don't use "get or create" for lazy initialization, though. 

### Serialization

Use "serializer" for objects whose purpose is serializing or deserializing some type of object (`RecipeSerializer`). Use
"serialize" and "deserialize" for methods only when serializing or deserializing an object other than the one the method is in.

Use "from" for static methods that create an object of the method owner's type (`fromJson`, `fromNbt`, `fromString`). Use "to"
for methods that convert an object to another type (`toString`, `toLong`, `toNbt`).

Use "read" for non-static methods that load data into the object. Use "write" for methods that save data to an *existing* 
object passed as a parameter.

### Factories and builders

Use "factory" for objects whose purpose is creating other objects.

Use "builder" for objects whose purpose is helping with the creation of an immutable object. Name builder methods the same
as the field they're setting, without any prefix.

### Collections

Use a plural name for collections and maps rather than the words "list", "set", "array", etc., unless it's a collection of
collections or there are several collections of different types containing the same objects (`entities`, `entityLists`).

When it's enough, name maps based on the value type. Otherwise, name it in the "`valuesByKeys`" format.

### Coordinates

Coordinates can be named `x`, `y`, and `z` when it's clear what they represent. If clarification is needed, add a word in
front of the coordinate (`velocityX`, not `xVelocity`).

Name screen coordinates `x` and `y`, rather than `left` and `top`.

## Javadocs

Write sentences for class, method and fields javadocs, starting with an uppercase and ending with a period. Start method docs with verbs, like `Gets` or `Called`. Use HTML tags such as `<p>` if the docs have several paragraphs.

Write quick descriptions for parameter javadocs as well as `@return` tags, with no uppercase or period. Add parameter docs to the parameter itself instead of using the `@param` tag.

Use `@link`, `@linkplain` and `@see` tags to refer to other parts of the code.

## Mojang names

**Do not use names from Mojang's obfuscation maps.**

Use names that match names in strings in the vanilla code, unless that string is outdated or inaccurate. This avoids confusion,
especially from new modders who may not understand what an class exception message is referring to.

Even if a known Mojang name doesn't appear in any strings, it's a good idea to use it since the official name is a good
indicator of the the class's actual purpose and makes it less likely the name will have to be changed in the future. For
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
