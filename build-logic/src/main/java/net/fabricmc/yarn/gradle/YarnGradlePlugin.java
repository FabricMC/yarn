package net.fabricmc.yarn.gradle;

import net.fabricmc.yarn.gradle.task.CombineUnpickDefinitionsTask;
import net.fabricmc.yarn.gradle.task.GeneratePackageInfoMappingsTask;
import net.fabricmc.yarn.gradle.task.JavadocLintTask;
import net.fabricmc.yarn.gradle.task.RemapUnpickDefinitionsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class YarnGradlePlugin implements Plugin<Project> {
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
