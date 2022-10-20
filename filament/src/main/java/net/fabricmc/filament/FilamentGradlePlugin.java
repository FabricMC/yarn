package net.fabricmc.filament;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import net.fabricmc.filament.task.CombineUnpickDefinitionsTask;
import net.fabricmc.filament.task.GeneratePackageInfoMappingsTask;
import net.fabricmc.filament.task.JavadocLintTask;
import net.fabricmc.filament.task.RemapUnpickDefinitionsTask;

public final class FilamentGradlePlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		project.getTasks().register("generatePackageInfoMappings", GeneratePackageInfoMappingsTask.class);
		project.getTasks().register("javadocLint", JavadocLintTask.class);

		var combineUnpickDefinitions = project.getTasks().register("combineUnpickDefinitions", CombineUnpickDefinitionsTask.class);
		project.getTasks().register("remapUnpickDefinitionsIntermediary", RemapUnpickDefinitionsTask.class, task -> {
			task.dependsOn(combineUnpickDefinitions);
			task.getInput().set(combineUnpickDefinitions.flatMap(CombineUnpickDefinitionsTask::getOutput));
			task.getSourceNamespace().set("named");
			task.getTargetNamespace().set("intermediary");
		});
	}
}
