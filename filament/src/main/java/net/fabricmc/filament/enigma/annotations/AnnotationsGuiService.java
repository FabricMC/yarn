package net.fabricmc.filament.enigma.annotations;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.UncheckedIOException;

import cuchaz.enigma.api.EnigmaIcon;
import cuchaz.enigma.api.I18n;
import cuchaz.enigma.api.service.GuiService;
import cuchaz.enigma.api.view.GuiView;
import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.EntryReferenceView;
import cuchaz.enigma.api.view.entry.EntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.ClassAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.GenericAnnotationData;
import net.fabricmc.loom.configuration.providers.mappings.extras.annotations.MethodAnnotationData;

import javax.swing.KeyStroke;

public class AnnotationsGuiService implements GuiService {
	private static final EnigmaIcon ICON;
	private static final EnigmaIcon ICON_DARK;

	static {
		try {
			ICON = EnigmaIcon.loadResource("icons/annotation_gutter.svg");
			ICON_DARK = EnigmaIcon.loadResource("icons/annotation_gutter_dark.svg");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private final AnnotationsEnigmaPlugin plugin;

	public AnnotationsGuiService(AnnotationsEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void addToEditorContextMenu(GuiView gui, MenuRegistrar registrar) {
		registrar.addSeparator();
		registrar.add("annotations.edit")
				.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK))
				.setEnabledWhen(() -> gui.isCursorOnDeclaration() && supportsAnnotationsMenu(gui.getCursorReference()))
				.setAction(() -> {
					if (gui.isCursorOnDeclaration() && supportsAnnotationsMenu(gui.getCursorReference())) {
						AnnotationsEditor.open(gui, plugin, gui.getCursorReference().getEntry());
					}
				});
	}

	private static boolean supportsAnnotationsMenu(@Nullable EntryReferenceView reference) {
		if (reference == null) {
			return false;
		}

		EntryView entry = reference.getEntry();
		return entry instanceof ClassEntryView || entry instanceof FieldEntryView || entry instanceof MethodEntryView;
	}

	@Override
	public void addGutterMarkers(GuiView gui, EntryView entry, GutterMarkerAdder gutter) {
		if (plugin.project == null) {
			return;
		}

		EntryView deobfEntry = plugin.project.deobfuscate(entry);

		boolean hasAnnotations = switch (deobfEntry) {
		case ClassEntryView classEntry -> hasData(plugin.data.classes().get(classEntry.getFullName()));
		case FieldEntryView fieldEntry -> {
			ClassAnnotationData classData = plugin.data.classes().get(fieldEntry.getParent().getFullName());
			yield classData != null && hasData(classData.getFieldData(fieldEntry.getName(), fieldEntry.getDescriptor()));
		}
		case MethodEntryView methodEntry -> {
			ClassAnnotationData classData = plugin.data.classes().get(methodEntry.getParent().getFullName());
			yield classData != null && hasData(classData.getMethodData(methodEntry.getName(), methodEntry.getDescriptor()));
		}
		default -> false;
		};

		if (hasAnnotations) {
			gutter.addMarker(gui.isDarkTheme() ? ICON_DARK : ICON, GutterMarkerAlignment.RIGHT)
					.setTooltip(I18n.translate("annotations.gutter.tooltip"))
					.setClickAction(() -> AnnotationsEditor.open(gui, plugin, entry));
		}
	}

	private static boolean hasData(@Nullable ClassAnnotationData data) {
		return data != null && (!data.annotationsToRemove().isEmpty() || !data.annotationsToAdd().isEmpty() || !data.typeAnnotationsToRemove().isEmpty() || !data.typeAnnotationsToAdd().isEmpty());
	}

	private static boolean hasData(@Nullable GenericAnnotationData data) {
		return data != null && (!data.annotationsToRemove().isEmpty() || !data.annotationsToAdd().isEmpty() || !data.typeAnnotationsToRemove().isEmpty() || !data.typeAnnotationsToAdd().isEmpty());
	}

	private static boolean hasData(@Nullable MethodAnnotationData data) {
		return data != null && (!data.annotationsToRemove().isEmpty() || !data.annotationsToAdd().isEmpty() || !data.typeAnnotationsToRemove().isEmpty() || !data.typeAnnotationsToAdd().isEmpty() || data.parameters().values().stream().anyMatch(AnnotationsGuiService::hasData));
	}
}
