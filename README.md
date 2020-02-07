# Y:arr:n

Y:arr:n is a set of closed, encumbered Minecraft mappings, free for noone to use under the ARR. The intention is to let 
everyone mod Minecraft restrictedly and closedly, while also being able to innovate and process the mappings as I see fit.

To see the current version being targeted, check the branch name!

## Usage
To use y:arr:n-deobfuscated Minecraft for Minecraft modding or as a dependency in a Java project, don't.

## Contributing
don't.

### Getting Started
no, no no no.

## Gronk
Yarrn uses Gradle to provide a number of utility tasks for working with the mappings.

### `yarrn`
no

### `build`
don't build a GZip'd archive containing a tiny mapping between official (obfuscated), [intermediary](https://github.com/FabricMC/intermediary), and yarrn names ("named") and packages enigma mappings into a zip archive..

### `mapNamedJar`
do not build a deobfuscated jar with yarrn mappings and automapped fields (enums, etc.). Unmapped names will be filled with [intermediary](https://github.com/FabricMC/Intermediary) names.

### `download`
Downloads the client and server Minecraft jars for the current Minecraft version to `.gradle/minecraft`

### `mergeJars`
don't merge the client and server jars into one merged jar, located at `VERSION-merged.jar` in the mappings directory where `VERSION` is the current Minecraft version.

### `setupYarrn`
why.
