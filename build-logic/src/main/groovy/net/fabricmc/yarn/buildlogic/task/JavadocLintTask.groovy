package net.fabricmc.yarn.buildlogic.task

import cuchaz.enigma.ProgressListener
import cuchaz.enigma.translation.mapping.EntryMapping
import cuchaz.enigma.translation.mapping.serde.MappingFileNameFormat
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader
import cuchaz.enigma.translation.mapping.tree.EntryTree
import cuchaz.enigma.translation.representation.entry.Entry
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry
import cuchaz.enigma.translation.representation.entry.MethodEntry
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

@CompileStatic
class JavadocLintTask extends DefaultTask {
    private static final String PARAMETER_TAG_START = "@param "

    @InputDirectory
    DirectoryProperty mappingDirectory = project.objects.directoryProperty()

    JavadocLintTask() {
        // Only run when inputs have changed
        outputs.upToDateWhen { true }
    }

    @TaskAction
    def run() {
        def saveParameters = new MappingSaveParameters(MappingFileNameFormat.BY_DEOBF)
        def directory = mappingDirectory.getAsFile().get().toPath()
        EntryTree<EntryMapping> mappings = EnigmaMappingsReader.DIRECTORY.read(directory, ProgressListener.none(), saveParameters)
        def errors = []

        mappings.allEntries.parallel().forEach { Entry<?> entry ->
            EntryMapping mapping = mappings.get(entry)
            String javadoc = mapping.javadoc

            if (javadoc != null && !javadoc.isEmpty()) {
                def localErrors = []

                if (entry instanceof LocalVariableEntry && entry.argument) {
                    if (javadoc.endsWith(".")) {
                        localErrors.add("parameter javadoc ends with '.'")
                    }

                    if (javadoc.charAt(0).isUpperCase()) {
                        String word = getFirstWord(javadoc)

                        // ignore single-letter "words" (like X or Z)
                        if (word.length() > 1) {
                            localErrors.add("parameter javadoc starts with uppercase word '${word}'")
                        }
                    }
                }

                if (entry instanceof MethodEntry) {
                    // check for non-type parameters declared as @param
                    if (javadoc.contains(PARAMETER_TAG_START)) {
                        int paramIndex = javadoc.indexOf(PARAMETER_TAG_START) + PARAMETER_TAG_START.length()

                        if (javadoc.length() <= paramIndex || javadoc.charAt(paramIndex) != '<' as char) {
                            localErrors.add("method contains parameter docs, which should be on the parameter itself")
                        }
                    }
                }

                // new rules can be added here in the future

                if (!localErrors.isEmpty()) {
                    def name = getFullName(mappings, entry as Entry /* cast to raw type for compilation */)

                    localErrors.forEach {
                        errors.add("$name: $it")
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            errors.forEach { println("lint: $it") }
            throw new GradleException("Found ${errors.size()} javadoc format errors! See the log for details.")
        }
    }

    private static String getFirstWord(String str) {
        int i = str.indexOf(' ')
        return i != -1 ? str.substring(0, i) : str
    }

    private static String getFullName(EntryTree<EntryMapping> mappings, Entry<?> entry) {
        def name = mappings.get(entry).targetName

        if (entry.parent != null) {
            name = "${getFullName(mappings, entry.parent as Entry<?>)}.$name"
        }

        return name
    }
}
