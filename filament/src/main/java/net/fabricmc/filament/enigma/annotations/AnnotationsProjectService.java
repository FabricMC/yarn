package net.fabricmc.filament.enigma.annotations;

import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.ProjectView;

public class AnnotationsProjectService implements ProjectService {
	private final AnnotationsEnigmaPlugin plugin;

	public AnnotationsProjectService(AnnotationsEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onProjectOpen(ProjectView project) {
		plugin.project = project;
		project.registerForInverseMappings();
		plugin.index = AnnotationsIndex.index(project);
	}

	@Override
	public void onProjectClose(ProjectView project) {
		plugin.project = null;
		plugin.index = null;
	}
}
