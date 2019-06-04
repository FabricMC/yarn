# Naming conventions

## General

Use `UpperCamelCase` for class names. Use `lowerCamelCase` for method names, variable names, and names of fields that are not
static and final. Always use `UPPER_SNAKE_CASE` for names of fields that are both static and final.

Method names should always be verbal phrases (`tick`, `getCarversForStep`). Non-boolean field and variable names and class names
should be noun phrases (`ChunkRegion`, `color`). Boolean field and variable names should always be adjectival phrases or present
tense verbal phrases (`powered`, `canOpen`), avoiding the `is` and `has` prefixes when possible (`colored`, not `isColored` or
`hasColor`).

To make code as easy to read as possible, keep names in the natural language order. For example, a class representing a "chest
block entity" should be named `ChestBlockEntity`, not `BlockEntityChest`. Though prefix naming may be helpful for grouping
classes together in an IDE's tree view, reading and writing code is done much more often than browsing files.

Use American English (`color`, not `colour`) for consistency throughout Yarn and with known Mojang names.

Omit words that are redundant because of method parameter or the owner class's name. For example, use `getChunk(BlockPos pos)`
rather than `getChunkAtPosition(BlockPos pos)`, and `create` rather than `createAchievement` for a method is in the `Achievement`
class. Don't avoid overloading methods or shadowing fields.

Names should be as descriptive as possible, so don't omit important words. When naming something always look at all its usages
(including overriding methods and inheriting classes).

Consistency is important as it makes code more readable and names easier to memorize. When possible, use terms that are present
in other Yarn names, in libraries used by Minecraft, or in vanilla strings. See the "Common names" and "Mojang names" for more
information.

## Acronym and abbreviations

Try to avoid acronyms and abbreviations unless it's a common one that everyone knows, and other yarn names involving the same
word all use its abbreviated form. Full names are easier to read quickly and remember ("Which words were abbreviated?") and
they often don't take more time to type thanks to IDE autocompletion. The common abbreviations you *should* use are:

 - "id" for "identifier"
 - "pos" for "position"
 - "NBT" for "named binary tag"
 - "init" for "initialize"
 - Any abbreviations used by Java or libraries ("json", "html", etc.)

Treat acronyms as single words rather than capitalising every letter. This improves readability (compare `JsonObject` and
`JSONObject`) and is consistent with Mojang naming (a known name is `NbtIo`).

## Mojang names

Always use names that match names in strings in the vanilla code, unless that string is outdated or inaccurate. This avoids
confusion, especially from new modders who may not understand what an class exception message is referring to.

Even if a known Mojang name doesn't appear in any strings, it's a good idea to use it, since the official name is a good
indicator of the the class's actual purpose and what changes Mojang may do to it in the future. For example, don't name a class
that Mojang calls `BedrockBlock` `NoSpawningBlock`, even if its only purpose is to disable mob spawning, because Mojang may
decide to override more methods in that class, breaking mods that were using it in an unexpected way. Additionally, using a
known Mojang name it less likely that the name will have to be changed in the future.

There are however three exceptions to this rule:
 - Use "world" for what Mojang calls "level" (see https://github.com/FabricMC/yarn/issues/89)
 - Use "container" for what Mojang calls "menu" (see https://github.com/FabricMC/yarn/issues/386)
 - Use "inventory" for what Mojang calls "container" (no issue yet, requires renaming "container" first)

## Packages

Package names should always be singular, to respect Java conventions. Try to respect the Mojang package structure to avoid
visibility problems in the future.

## Common names

This section lists common names that are used throughout Yarn. There's no particular reason these are the names are being used
instead of others, but you should use them for consistency.

### Ticks and updates

Use the term "tick" for updates done once per tick. Use "update" for other kind of updates.

### Value last tick

When a field (or getter) is the value that something had last tick (for interpolation, for example), prefix the field with the
`last` prefix, (`lastX`, `lastWidth`, etc.).

### Getters, setters, and creators

Use the word "get" for getters and other methods that calculate some property with no side effects other than possibly caching
the value in a private field only used by that method.

Use the word "set" for methods that set some property.

Use the word "create" for methods that create a new instance of some object. Use the term "get or create" for methods that
create a new instance only if one does not already exist. Don't use "get or create" for lazy initialization, though. 

### Serializing and deserializing

Use "serializer" for naming classes whose purpose is serializing or deserializing some type of object (ex. `RecipeSerializer`).
Use the words "serialize" and "deserialize" only when refering to serializing or deserializing some object other.

Use "from" for static methods that create an object of the method owner's type (`fromJson`, `fromNbt`, `fromString`, etc.). Use
"to" for methods that convert an object to another type (`toString`, `toLong`, `toNbt`, etc.).

Use "read" for non-static methods that load data into a class. Use "write" for methods that save data to an *existing* object
passed as a parameter.

### Factories and builders

Use the word "factory" (rather than "provider", etc.) for factories. Use "builder" for builders.

### Collections

Use a plural name for collections and maps rather than the words "list", "set", "array", etc., unless it's a collection of
collection or there are several collections of different types containing the same objects (`entities`, `entityLists`, etc.).

When it's enough, name maps based on the value type. Otherwise, name it in the "`valuesByKeys`" format.

## Information that should not be present in names

Avoid naming methods based on implementation details.

Avoid including Java-related information in names. For example, don't prefix class names with `I`, `Enum`, or `Abstract` and
don't prefix methods with `private`. Instead, try to find meaningful names to describe differences between classes. In the
case of abstract classes, this may involve renaming subclasses to more specific names.
