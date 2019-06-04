# Naming Conventions

## Casing

Use `UpperCamelCase` for class names. Use `lowerCamelCase` for method, variable, and fields that are not static and final. Use `UPPER_SNAKE_CASE` for all fields that are both static and final.

## Word order

To make code as easy to read as possible, keep names in the natural English language order. For example, a class representing a "chest block entity" should be named `ChestBlockEntity`, not `BlockEntityChest`. Though using prefix naming may be helpful for grouping classes together in the IDE's tree view, reading and writing code is done much more often than browsing files.

## Acronym and abbreviations

Try to avoid acronyms and abbreviations unless it's a common one that everyone knows, and other yarn names involving the same word all use its abbreviated form. Full names are easier to read quickly and remember ("Which words were abbreviated?") and they often don't take more time to type due to IDE autocompletion.

Treat acronyms as single words rather than capitalising every letter. This improves readability (compare `JsonObject` and `JSONObject`) and is consistent with Mojang naming (a known name is `NbtIo`).

## Mojang names

Always use names that match names in strings in the original code, unless that string is outdated or inaccurate. This 

Even if a known Mojang name doesn't appear in any strings, it's a good idea to use cit, since the official name gives more information about the class's purpose and what Mojang may decide to do with it in the future.

However, there are three exceptions to this rule (all have open issues suggesting a change to the official Mojang name):
 - Use "container" for what Mojang calls "menu"
 - Use "inventory" for what Mojang calls "container"
 - Use "world" for what Mojang calls "level"

## Other conventions

Avoid including Java-related information in names. For example, don't prefix class names with `I`, `Enum`, or `Abstract` and don't prefix methods with `private`. Instead, try to find meaningful names to describe differences between classes. In the case of abstract classes, this may involve renaming subclasses to more specific names.
