package net.fabricmc.yarn.buildlogic.task

import cuchaz.enigma.ProgressListener
import cuchaz.enigma.translation.mapping.EntryMapping
import cuchaz.enigma.translation.mapping.serde.MappingFileNameFormat
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader
import cuchaz.enigma.translation.mapping.tree.EntryTree
import cuchaz.enigma.translation.representation.entry.Entry
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

@CompileStatic
class JavadocLintTask extends DefaultTask {
    @InputFiles
    DirectoryProperty mappingDirectory = project.objects.directoryProperty()

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
                        localErrors.add("parameter javadoc starts with uppercase word '${getFirstWord(javadoc)}'")
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
