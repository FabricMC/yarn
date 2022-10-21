package net.fabricmc.filament.task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.filament.util.FileUtil;

public class GeneratePackageInfoMappingsTask extends DefaultTask {
	private final RegularFileProperty inputJar = getProject().getObjects().fileProperty();
	private final Property<String> packageName = getProject().getObjects().property(String.class);
	private final DirectoryProperty outputDir = getProject().getObjects().directoryProperty();

	@InputFile
	public RegularFileProperty getInputJar() {
		return inputJar;
	}

	@Input
	public Property<String> getPackageName() {
		return packageName;
	}

	@OutputDirectory
	public DirectoryProperty getOutputDir() {
		return outputDir;
	}

	@TaskAction
	public void run() throws IOException {
		getProject().getLogger().lifecycle("Scanning {} for package-info classes", inputJar.get().getAsFile());

		FileUtil.deleteDirectory(outputDir.get().getAsFile());

		try (ZipFile zipFile = new ZipFile(inputJar.get().getAsFile())) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();

				if (entry.getName().endsWith(".class")) {
					try (InputStream is = zipFile.getInputStream(entry)) {
						processEntry(entry.getName(), is);
					}
				}
			}
		}
	}

	private void processEntry(String name, InputStream inputStream) throws IOException {
		name = name.replace(".class", "");

		if (name.contains("$")) {
			// Dont care about inner classes
			return;
		}

		ClassReader classReader = new ClassReader(inputStream);
		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);

		if (classNode.access != (Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_INTERFACE)) {
			// We only care about abstract synthetic interfaces, hopefully this is specific enough
			return;
		}

		if (classNode.methods.size() > 0 || classNode.fields.size() > 0 || classNode.interfaces.size() > 0) {
			// Nope cannot be a package-info
			return;
		}

		generateMapping(name);
	}

	private void generateMapping(String name) throws IOException {
		String inputName = name.substring(name.lastIndexOf("/") + 1);
		String className = "PackageInfo" + name.substring(name.lastIndexOf("_") + 1);
		String fullName = packageName.get() + className;
		File mappingsFile = new File(outputDir.get().getAsFile(), className + ".mapping");

		mappingsFile.getParentFile().mkdirs();

		try (PrintWriter writer = new PrintWriter(new FileWriter(mappingsFile))) {
			writer.printf("CLASS net/minecraft/%s %s", inputName, fullName);
			writer.print('\n'); // println is platform-dependent and may produce CRLF.
		}
	}
}

