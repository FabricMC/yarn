package net.fabricmc.filament.enigma.unpick;

import java.util.ArrayList;

import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.ClassEntryView;

public class UnpickProjectService implements ProjectService {
	private final UnpickEnigmaPlugin plugin;

	public UnpickProjectService(UnpickEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onProjectOpen(ProjectView project) {
		plugin.project = project;
		project.registerForInverseMappings();
		refreshClassesInPackages();

		project.addDataInvalidationListener(event -> refreshClassesInPackages());
	}

	@Override
	public void onProjectClose(ProjectView project) {
		plugin.project = null;
	}

	private void refreshClassesInPackages() {
		synchronized (UnpickEnigmaPlugin.UNINLINER_CREATION_LOCK) {
			plugin.classesInPackages.clear();

			for (String clazz : plugin.project.getProjectClasses()) {
				String mappedClass = plugin.project.deobfuscate(ClassEntryView.create(clazz)).getFullName();
				int slashIndex = mappedClass.lastIndexOf('/');

				if (slashIndex == -1) {
					plugin.classesInPackages.computeIfAbsent("", k -> new ArrayList<>()).add(mappedClass);
				} else {
					plugin.classesInPackages.computeIfAbsent(mappedClass.substring(0, slashIndex), k -> new ArrayList<>()).add(mappedClass.substring(slashIndex + 1));
				}
			}
		}
	}
}
