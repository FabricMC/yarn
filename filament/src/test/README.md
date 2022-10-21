The tests for Filament are mostly Gradle projects that are automatically tested using JUnit 5.

## Structure

### `/projects/sharedData`

Data files shared between tests. This includes large files like a build of Yarn mappings.

### `/projects/javadocLint`

Test project for the `javadocLint` task (`JavadocLintTask`).

### `/projects/unpickDef`

Test project for the `combineUnpickDefinitions` and `remapUnpickDefinitionsIntermediary` tasks
(`CombineUnpickDefinitionsTask` and `RemapUnpickDefinitionsTask`).
