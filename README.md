# Yarn

Yarn is a set of open, unencumbered Minecraft mappings, free for everyone to use under the Creative Commons Zero license. The intention is to let 
everyone mod Minecraft freely and openly, while also being able to innovate and process the mappings as they see fit.

To see the current version being targetted, check the branch name!

## Contributing

Please remember that copying and pasting mappings from alternate projects under more restrictive licenses (such as MCP) is **completely forbidden** without explicit permission from the 
owners of said mappings. Discussing the naming approaches used in said projects is also not welcome - you have been warned.
However, it is a good idea to consult name changes with other people - use pull requests or our community spaces to ask questions!

### Getting Started

1. Fork and clone the repo
2. Run `./gradlew yarn` (Linux, macOS) or `gradlew yarn` (Windows)
3. Profit

To use Yarn in your IDE of choice, or as part of a full Fabric development environment, check out the [Fabric example mod](https://github.com/FabricMC/fabric-example-mod).

1. Fork/Clone the repo
2. Run `./gradlew genSources [idea|eclipse]`, selecting one of `idea` or `eclipse` based on whether you want to use IntelliJ or Eclipse.
3. Import `build.gradle`, or the repository itself, into your IDE.

## Gradle
Yarn uses Gradle to provide a number of utility tasks for working with the mappings.

### `download`
Downloads the client and server Minecraft jars for the current Minecraft version to `.gradle/minecraft`

### `mergeJars`
Merges the client and server jars into one merged jar, located at `VERSION-merged.jar` in the mappings directory where `VERSION` is the current Minecraft version.

### `setupYarn`
`download` and `mergeJars`

### `yarn`
`setupYarn` and download and launch the latest version of Enigma automatically configured to use the merged jar and the mappings.
