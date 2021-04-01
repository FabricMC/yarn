package net.fabricmc.yarn.buildlogic.task

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

import java.util.zip.ZipFile

@CompileStatic
class GeneratePackageInfoMappingsTask extends DefaultTask {
    @InputFile
    final RegularFileProperty inputJar = project.objects.fileProperty()
    @OutputDirectory
    final Property<String> packageName = project.objects.property(String.class)
    @OutputDirectory
    final DirectoryProperty outputDir = project.objects.directoryProperty()

    @TaskAction
    def run() {
        project.logger.lifecycle("Scanning ${inputJar.get().asFile} for package-info classes")

        outputDir.get().asFile.deleteDir()

        new ZipFile(inputJar.get().asFile).withCloseable {zipFile ->
            zipFile.entries().iterator().forEachRemaining({ entry ->
                if (entry.name.endsWith(".class")) {
                    zipFile.getInputStream(entry).withCloseable {is ->
                        processEntry(entry.name, is)
                    }
                }
            })
        }
    }

    private void processEntry(String name, InputStream inputStream) {
        name = name.replace(".class", "")

        if (name.contains("\$")) {
            // Dont care about inner classes
            return
        }

        ClassReader classReader = new ClassReader(inputStream)
        ClassNode classNode = new ClassNode()
        classReader.accept(classNode, 0)

        if (classNode.access != (Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_INTERFACE )) {
            // We only care about abstract synthetic interfaces, hopefully this is specific enough
            return
        }

        if (classNode.methods.size() > 0 || classNode.fields.size() > 0 || classNode.interfaces.size() > 0) {
            // Nope cannot be a package-info
            return
        }

        generateMapping(name)
    }

    private void generateMapping(String name) {
        String className = name.substring(name.lastIndexOf("/") + 1)
        String fullName = packageName.get() + className;
        File mappingsFile = new File(outputDir.get().asFile, "${className}.mapping")

        mappingsFile.parentFile.mkdirs()

        mappingsFile.text = """\
                            CLASS net/minecraft/$className $fullName 
                            """.stripIndent()
    }
}
