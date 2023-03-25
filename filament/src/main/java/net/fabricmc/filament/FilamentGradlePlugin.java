package net.fabricmc.filament;

import java.io.File;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.filament.task.CombineUnpickDefinitionsTask;
import net.fabricmc.filament.task.DownloadTask;
import net.fabricmc.filament.task.GeneratePackageInfoMappingsTask;
import net.fabricmc.filament.task.JavadocLintTask;
import net.fabricmc.filament.task.RemapUnpickDefinitionsTask;
import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.filament.task.minecraft.ExtractBundledServerTask;
import net.fabricmc.filament.task.minecraft.MergeMinecraftTask;
import net.fabricmc.filament.task.minecraft.MinecraftLibrariesTask;
import net.fabricmc.filament.task.minecraft.MinecraftVersionMetaTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public final class FilamentGradlePlugin implements Plugin<Project> {
	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	public void apply(Project project) {
		final FilamentExtension extension = project.getExtensions().create("filament", FilamentExtension.class);
		final TaskContainer tasks = project.getTasks();

		var minecraftMetaTask = tasks.register("downloadMinecraftMeta", MinecraftVersionMetaTask.class);
		var metaProvider = MinecraftVersionMetaTask.readMetadata(minecraftMetaTask);

		var minecraftClient = tasks.register("downloadMinecraftClientJar", DownloadTask.class, task -> {
			Provider<MinecraftVersionMeta.Download> downloadProvider = metaProvider.map(meta -> meta.download("client"));
			task.getUrl().set(downloadProvider.map(MinecraftVersionMeta.Download::url));
			task.getSha1().set(downloadProvider.map(MinecraftVersionMeta.Download::sha1));

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "client.jar"));
		});
		var minecraftServer = tasks.register("downloadMinecraftServerJar", DownloadTask.class, task -> {
			Provider<MinecraftVersionMeta.Download> downloadProvider = metaProvider.map(meta -> meta.download("server"));
			task.getUrl().set(downloadProvider.map(MinecraftVersionMeta.Download::url));
			task.getSha1().set(downloadProvider.map(MinecraftVersionMeta.Download::sha1));

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "server_bundle.jar"));
		});
		var extractBundledServer = tasks.register("extractBundledServer", ExtractBundledServerTask.class, task -> {
			task.dependsOn(minecraftServer);
			task.getServerJar().set(getOutput(minecraftServer));
			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "server.jar"));
		});
		tasks.register("mergeMinecraftJars", MergeMinecraftTask.class, task -> {
			task.getClientJar().set(getOutput(minecraftClient));
			task.getServerJar().set(getOutput(extractBundledServer));

			task.getOutputFile().set(new File(extension.getMinecraftDirectory(), "merged.jar"));
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

		tasks.register("minecraftLibraries", MinecraftLibrariesTask.class, task -> {
			task.dependsOn(minecraftMetaTask);

			var files = MinecraftVersionMetaTask.readMetadata(minecraftMetaTask)
					.map(meta -> MinecraftLibrariesTask.getDependencies(project, meta))
					.map(dependencies -> project.getConfigurations().detachedConfiguration(dependencies).resolve());
			task.getFiles().from(files);
		});
	}

	private Provider<RegularFile> getOutput(TaskProvider<? extends FileOutputTask> taskProvider) {
		return taskProvider.flatMap(FileOutputTask::getOutputFile);
	}
}
