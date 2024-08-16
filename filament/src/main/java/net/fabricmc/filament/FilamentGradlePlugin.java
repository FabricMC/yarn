package net.fabricmc.filament;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.filament.task.CombineUnpickDefinitionsTask;
import net.fabricmc.filament.task.DownloadTask;
import net.fabricmc.filament.task.GeneratePackageInfoMappingsTask;
import net.fabricmc.filament.task.JavadocLintTask;
import net.fabricmc.filament.task.RemapUnpickDefinitionsTask;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.filament.task.minecraft.ExtractBundledServerTask;
import net.fabricmc.filament.task.minecraft.MergeMinecraftTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.gradle.GradleUtils;

public final class FilamentGradlePlugin implements Plugin<Project> {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Override
	public void apply(Project project) {
		final FilamentExtension extension = project.getExtensions().create("filament", FilamentExtension.class);
		final TaskContainer tasks = project.getTasks();

		var metaProvider = extension.getMinecraftVersionMetadata();

		var minecraftClient = tasks.register("downloadMinecraftClientJar", DownloadTask.class, task -> {
			Provider<MinecraftVersionMeta.Download> downloadProvider = metaProvider.map(meta -> meta.download("client"));
			task.getUrl().set(downloadProvider.map(MinecraftVersionMeta.Download::url));
			task.getSha1().set(downloadProvider.map(MinecraftVersionMeta.Download::sha1));

			task.getOutput().set(extension.getMinecraftFile("client.jar"));
		});
		var minecraftServer = tasks.register("downloadMinecraftServerJar", DownloadTask.class, task -> {
			Provider<MinecraftVersionMeta.Download> downloadProvider = metaProvider.map(meta -> meta.download("server"));
			task.getUrl().set(downloadProvider.map(MinecraftVersionMeta.Download::url));
			task.getSha1().set(downloadProvider.map(MinecraftVersionMeta.Download::sha1));

			task.getOutput().set(extension.getMinecraftFile("server_bundle.jar"));
		});
		var extractBundledServer = tasks.register("extractBundledServer", ExtractBundledServerTask.class, task -> {
			task.dependsOn(minecraftServer);
			task.getInput().set(getOutput(minecraftServer));
			task.getOutput().set(extension.getMinecraftFile("server.jar"));
		});
		tasks.register("mergeMinecraftJars", MergeMinecraftTask.class, task -> {
			task.getClientJar().set(getOutput(minecraftClient));
			task.getServerJar().set(getOutput(extractBundledServer));

			task.getOutput().set(extension.getMinecraftFile("merged.jar"));
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

		var cleanFilament = tasks.register("cleanFilament", Delete.class, task -> task.delete(extension.getCacheDirectory()));
		tasks.named("clean", task -> task.dependsOn(cleanFilament));

		var minecraftLibraries = project.getConfigurations().register("minecraftLibraries");

		GradleUtils.afterSuccessfulEvaluation(project, () -> {
			var name = minecraftLibraries.getName();

			for (Dependency dependency : getDependencies(metaProvider.get(), project.getDependencies())) {
				project.getDependencies().add(name, dependency);
			}
		});
	}

	private Provider<? extends RegularFile> getOutput(TaskProvider<? extends WithFileOutput> taskProvider) {
		return taskProvider.flatMap(WithFileOutput::getOutput);
	}

	private Dependency[] getDependencies(MinecraftVersionMeta meta, DependencyHandler dependencyHandler) {
		return meta.libraries().stream()
				.filter(library -> library.artifact() != null)
				.map(library -> dependencyHandler.create(library.name()))
				.toArray(Dependency[]::new);
	}
}
