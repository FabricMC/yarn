# Yarn

> [!NOTE]
> Yarn is no longer being updated to new Minecraft versions past Minecraft 1.21.11. The goal now is to make the best mapping set for these obfuscated versions of Minecraft!

Yarn is a set of open, unencumbered Minecraft mappings, free for everyone to use under the [Creative Commons Zero license](/LICENSE). The intention is to let everyone mod Minecraft freely and openly, while also being able to innovate and process the mappings as they see fit.

Some icons in Filament, which we use to develop Yarn, are licensed under Apache-2.0. These icons can be found in the `filament/src/main/resources/icons` directory. They are not shipped with releases of Yarn.

Yarn has been released for Minecraft 1.14 through 1.21.11 across hundreds of Git branches. To see the Minecraft version being targeted, check the branch name!

## Usage

To use the Yarn-mapped Minecraft API for Minecraft modding or as a dependency in a Java project, you can use the [Fabric Loom](https://github.com/fabricmc/fabric-loom) Gradle plugin. With Fabric Loom, you can configure your `build.gradle` file to indicate that you would like to use the Yarn mappings:

```gradle
dependencies {
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
	modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"

	// Plus any other dependencies you are using!
}
```

You can find the latest Yarn version for a given Minecraft version from the [developing with Fabric](https://fabricmc.net/develop) page.

## Contributing

Please remember that copying and pasting mappings from alternate projects under more restrictive licenses (such as MCP, Spigot's or Mojang's obfuscation maps), as well as unobfuscated Minecraft releases, is **completely forbidden** without explicit permission from the owners of said mappings to distribute the names under the CC0 license. This includes using the names from those sources for inspiration. Discussing the naming approaches used in said projects is also not welcome - you have been warned. However, it is a good idea to consult name changes with other people - feel free to use pull requests or issues to ask questions!

Feel free to contribute pull requests adding names and documentation from newer Yarn releases to branches focusing on older Minecraft versions ("backports"). Just make sure what you are backporting is still accurate for the older Minecraft version!

Please have a look at the [Yarn naming conventions](/CONVENTIONS.md) before submitting mappings.

### Getting Started

1. Fork and clone the repo
2. Run `./gradlew yarn` (Linux, macOS) or `gradlew yarn` (Windows) to open [Enigma](https://github.com/FabricMC/Enigma), a user interface to easily edit the mappings
3. Commit and push your work to your fork
4. Open a pull request with your changes

Please note that to run the Yarn build script, **Java 21** or higher is required!

## Gradle

Yarn uses [Gradle](https://gradle.org/) to provide a number of utility tasks for working with the mappings.

### `yarn`

This task downloads and launches the latest version of [Enigma](https://github.com/FabricMC/Enigma), which is automatically configured to use the merged Minecraft JAR and the local directory's mappings.

Compared to launching Enigma externally, the Gradle task adds enhancements such as a plugin to automatically suggest certain names, including the names of enum members, constant fields, and record components.

### `yarnCommon`

This task is similar to `yarn`, but will only show "common" classes, which are classes present in both the Minecraft client and dedicated server JARs.

### `build`

Produces packaged artifacts for the Yarn mappings, including:

- `build/libs/yarn-{version}-mergedv2.jar`: a JAR containing a [Tiny v2](https://wiki.fabricmc.net/documentation:tiny2) mappings file between the `official`, `intermediary`, and `named` namespaces, as well as extras including Unpick and annotation definitions
- `build/libs/yarn-{version}-v2.jar`: similar to above, but only contains the `intermediary` and `named` namespaces
- `build/libs/yarn-{version}.jar`: a legacy JAR containing only a [Tiny v1](https://wiki.fabricmc.net/documentation:tiny) mappings file between the `official`, `intermediary`, and `named` namespaces
- `build/libs/yarn-{version}-javadoc.jar`: HTML [Javadoc](https://openjdk.org/groups/compiler/javadoc-architecture.html) output representing the Yarn-mapped Minecraft API

For reference, the mapping namespaces are as follows:

Namespace|Description|Examples
---|---|---
`official`|The names found in the unmodified Minecraft JARs, which often change between Minecraft versions.|`dlx`, `ws`, `gS`
`intermediary`|The names assigned by the [Intermediary](https://github.com/FabricMC/intermediary) project, which aim to remain consistent ("[match](https://github.com/FabricMC/Matcher)") across Minecraft versions.|`net/minecraft/class_1802`, `field_8567`, `method_7157`
`named`|The human-readable names provided by Yarn.|`net/minecraft/item/Items`, `POTATO`, `isSmall`

### `mapNamedJar`

Produces a deobfuscated Minecraft JAR at `build/{minecraft-version}-named.jar`. Intermediary names are used as fallbacks for Yarn names.

### `decompileVineflower` and `decompileCFR`

Produces decompiled, deobfuscated Minecraft source code in the `build/namedSrc` directory using the [Vineflower](https://github.com/Vineflower/vineflower) and [CFR](https://github.com/FabricMC/cfr) decompilers, respectively.

**Note:** The decompiled code is meant for reference and is not designed to be recompiled.

### `downloadMinecraftClientJar` and `downloadMinecraftServerJar`

Downloads the Minecraft client and dedicated server JARs for the current Minecraft version to the `.gradle/filament/{minecraft-version}` directory.

### `mergeJars`

Merges the Minecraft client and dedicated server JARs for the current Minecraft version into a single JAR at `.gradle/filament/{minecraft-version}/merged.jar`.

### `formatMappings`

Processes the mappings in the `mappings` directory, ensuring that they are consistently formatted and ordered. When editing mappings with Enigma, the mappings will be formatted in the same way automatically.
