package net.fabricmc.filament.enigma.unpick;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import javax.swing.KeyStroke;

import cuchaz.enigma.api.DataInvalidationEvent;
import cuchaz.enigma.api.service.GuiService;
import cuchaz.enigma.api.view.GuiView;
import cuchaz.enigma.api.view.ProjectView;
import cuchaz.enigma.api.view.entry.EntryReferenceView;
import cuchaz.enigma.api.view.entry.EntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.LocalVariableEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class UnpickGuiService implements GuiService {
	private final UnpickEnigmaPlugin plugin;

	public UnpickGuiService(UnpickEnigmaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onStart(GuiView gui) {
		plugin.theFrame = gui.getFrame();

		gui.getFrame().addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				plugin.isWindowFocused = true;

				if (plugin.unpickNeedsRefresh) {
					plugin.uninliner = null;
					plugin.unpickNeedsRefresh = false;

					if (plugin.project != null) {
						plugin.project.invalidateData(DataInvalidationEvent.InvalidationType.DECOMPILE);
					}
				}
			}

			@Override
			public void windowLostFocus(WindowEvent e) {
				plugin.isWindowFocused = false;
			}
		});
	}

	@Override
	public void addToEditorContextMenu(GuiView gui, MenuRegistrar registrar) {
		registrar.addSeparator();

		registrar.add("unpick.copyTargetReference")
				.setEnabledWhen(() -> getCursorTargetReference(gui) != null)
				.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK))
				.setAction(() -> {
					String textToCopy = getCursorTargetReference(gui);

					if (textToCopy != null) {
						Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy), null);
					}
				});
		registrar.add("unpick.copyConstantReference")
				.setEnabledWhen(() -> canBeConstant(gui.getCursorReference()))
				.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK))
				.setAction(() -> {
					if (!canBeConstant(gui.getCursorReference())) {
						return;
					}

					ProjectView project = gui.getProject();

					if (project == null) {
						return;
					}

					FieldEntryView obfField = (FieldEntryView) Objects.requireNonNull(gui.getCursorReference()).getEntry();
					FieldEntryView deobfField = project.deobfuscate(obfField);
					String textToCopy = deobfField.getParent().getFullName().replace('/', '.') + "." + deobfField.getName();
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy), null);
				});
	}

	@Nullable
	private String getCursorTargetReference(GuiView gui) {
		EntryReferenceView hoveredReference = gui.getCursorReference();

		if (hoveredReference == null) {
			return null;
		}

		ProjectView project = gui.getProject();

		if (project == null) {
			return null;
		}

		EntryView obfEntry = hoveredReference.getEntry();
		EntryView deobfEntry = project.deobfuscate(hoveredReference.getEntry());

		return switch (deobfEntry) {
		case FieldEntryView field -> "target_field %s %s %s".formatted(
				field.getParent().getFullName().replace('/', '.'),
				field.getName(),
				field.getDescriptor()
		);
		case MethodEntryView method -> "target_method %s %s %s".formatted(
				method.getParent().getFullName().replace('/', '.'),
				method.getName(),
				method.getDescriptor()
		);
		case LocalVariableEntryView ignored -> {
			int localIndex = getLocalIndex(project, (LocalVariableEntryView) obfEntry);
			yield localIndex == -1 ? null : "param " + localIndex;
		}
		default -> null;
		};
	}

	private int getLocalIndex(ProjectView project, LocalVariableEntryView local) {
		Type[] argTypes = Type.getArgumentTypes(local.getParent().getDescriptor());
		int methodAccess = project.getJarIndex().getEntryIndex().getAccess(local.getParent());
		int varIndex = (methodAccess & Opcodes.ACC_STATIC) != 0 ? 0 : 1;

		for (int localIndex = 0; localIndex < argTypes.length; localIndex++) {
			if (varIndex == local.getIndex()) {
				return localIndex;
			}

			varIndex += argTypes[localIndex].getSize();
		}

		return -1;
	}

	private boolean canBeConstant(@Nullable EntryReferenceView reference) {
		if (reference == null || plugin.project == null) {
			return false;
		}

		if (!(reference.getEntry() instanceof FieldEntryView field)) {
			return false;
		}

		int access = plugin.project.getJarIndex().getEntryIndex().getAccess(field);

		if ((access & Opcodes.ACC_FINAL) == 0) {
			return false;
		}

		return switch (field.getDescriptor()) {
		case "B", "C", "D", "F", "I", "J", "S", "Ljava/lang/String;", "Ljava/lang/Class;" -> true;
		default -> false;
		};
	}
}
