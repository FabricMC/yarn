package net.fabricmc.filament;

import java.io.File;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.fabricmc.filament.task.minecraft.DownloadMinecraftLibrariesTask;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.filament.task.CombineUnpickDefinitionsTask;
import net.fabricmc.filament.task.GeneratePackageInfoMappingsTask;
import net.fabricmc.filament.task.JavadocLintTask;
import net.fabricmc.filament.task.RemapUnpickDefinitionsTask;
import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.filament.task.minecraft.DownloadMinecraftJarTask;
import net.fabricmc.filament.task.minecraft.DownloadMinecraftManifestTask;
import net.fabricmc.filament.task.minecraft.ExtractBundledServerTask;
import net.fabricmc.filament.task.minecraft.MergeMinecraftTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public final class FilamentGradlePlugin implements Plugin<Project> {
	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	public void apply(Project project) {
		final FilamentExtension extension = project.getExtensions().create("filament", FilamentExtension.class);
		final TaskContainer tasks = project.getTasks();

		TaskProvider<DownloadMinecraftManifestTask> downloadMinecraftManifest = tasks.register("downloadMinecraftManifest", DownloadMinecraftManifestTask.class);
		Provider<MinecraftVersionMeta> minecraftVersionMetaProvider = downloadMinecraftManifest.flatMap(DownloadMinecraftManifestTask::getVersionMeta);

		TaskProvider<DownloadMinecraftJarTask> minecraftClient = tasks.register("downloadMinecraftClientJar", DownloadMinecraftJarTask.class, task -> {
			task.getVersionMeta().convention(minecraftVersionMetaProvider);
			task.getSide().set("client");

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "client.jar"));
		});
		TaskProvider<DownloadMinecraftJarTask> minecraftServer = tasks.register("downloadMinecraftServerJar", DownloadMinecraftJarTask.class, task -> {
			task.getVersionMeta().convention(minecraftVersionMetaProvider);
			task.getSide().set("server");

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "server_bundle.jar"));
		});
		TaskProvider<ExtractBundledServerTask> extractBundledServer = tasks.register("extractBundledServer", ExtractBundledServerTask.class, task -> {
			task.getServerJar().set(getOutput(minecraftServer));
			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "server.jar"));
		});
		tasks.register("mergeMinecraftJars", MergeMinecraftTask.class, task -> {
			task.getClientJar().set(getOutput(minecraftClient));
			task.getServerJar().set(getOutput(extractBundledServer));

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "merged.jar"));
		});
		tasks.register("minecraftLibraries", DownloadMinecraftLibrariesTask.class, task -> {
			task.getVersionMeta().set(minecraftVersionMetaProvider);
		});

		tasks.register("generatePackageInfoMappings", GeneratePackageInfoMappingsTask.class);
		tasks.register("javadocLint", JavadocLintTask.class);

		var combineUnpickDefinitions = tasks.register("combineUnpickDefinitions", CombineUnpickDefinitionsTask.class);
		tasks.register("remapUnpickDefinitionsIntermediary", RemapUnpickDefinitionsTask.class, task -> {
			task.dependsOn(combineUnpickDefinitions);
			task.getInput().set(combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput));
			task.getSourceNamespace().set("named");
			task.getTargetNamespace().set("intermediary");
		});
	}

	private Provider<RegularFile> getOutput(TaskProvider<? extends FileOutputTask> taskProvider) {
		return taskProvider.flatMap(FileOutputTask::getOutputFile);
	}
}
