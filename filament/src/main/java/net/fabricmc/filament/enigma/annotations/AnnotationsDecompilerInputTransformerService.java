package net.fabricmc.filament.enigma.annotations;

import cuchaz.enigma.api.service.DecompilerInputTransformerService;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.minecraft.AnnotationsApplyVisitor;

public class AnnotationsDecompilerInputTransformerService implements DecompilerInputTransformerService {
	private final AnnotationsEnigmaPlugin plugin;

	public AnnotationsDecompilerInputTransformerService(AnnotationsEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public ClassNode transform(ClassNode classNode) {
		if (plugin.project == null) {
			return classNode;
		}

		String deobfClassName = plugin.project.deobfuscate(ClassEntryView.create(classNode.name)).getFullName();
		ClassAnnotationData classData = plugin.data.classes().get(deobfClassName);

		if (classData == null) {
			return classNode;
		}

		ClassAnnotationData remappedClassData = new AnnotationsEnigmaRemapper(plugin.project).remap(deobfClassName, classData);
		ClassNode applied = new ClassNode();
		classNode.accept(new AnnotationsApplyVisitor.AnnotationsApplyClassVisitor(applied, remappedClassData));
		return applied;
	}
}
