package net.fabricmc.filament.enigma.unpick;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import cuchaz.enigma.api.DataInvalidationEvent;
import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.Ordering;
import cuchaz.enigma.api.service.DecompilerInputTransformerService;
import cuchaz.enigma.api.service.GuiService;
import cuchaz.enigma.api.service.I18nService;
import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.ProjectView;
import daomephsta.unpick.api.ConstantUninliner;
import org.jetbrains.annotations.NotNull;

public class UnpickEnigmaPlugin implements EnigmaPlugin {
	public static Path unpickDir;
	public ProjectView project;
	public boolean isWindowFocused = true;
	public JFrame theFrame;

	static final Object UNINLINER_CREATION_LOCK = new Object();
	public boolean unpickNeedsRefresh = true;
	public ConstantUninliner uninliner;

	public final Map<String, List<String>> classesInPackages = new HashMap<>();

	@Override
	public void init(EnigmaPluginContext ctx) {
		String unpickPath = System.getProperty("unpick.directory");

		if (unpickPath == null) {
			return;
		}

		unpickDir = Path.of(unpickPath);

		ctx.registerService("unpick:i18n", I18nService.TYPE, UnpickI18nService::new);
		ctx.registerService("unpick:project", ProjectService.TYPE, () -> new UnpickProjectService(this));
		ctx.registerService("unpick:gui", GuiService.TYPE, () -> new UnpickGuiService(this));
		ctx.registerService(
				"unpick:decompiler_input_transformer",
				DecompilerInputTransformerService.TYPE,
				() -> new UnpickDecompilerInputTransformerService(this),
				Ordering.after("annotations:decompiler_input_transformer") // make sure annotations are applied first so unpick can use them
		);

		registerFileWatcher();
	}

	public void showUserVisibleError(String title, String message) {
		if (theFrame != null) {
			JOptionPane.showMessageDialog(theFrame, message, title, JOptionPane.ERROR_MESSAGE);
		} else {
			System.out.println("[" + title + "] " + message);
		}
	}

	private void registerFileWatcher() {
		Thread thread = new Thread(() -> {
			try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
				Set<Path> alreadyWatchingDirs = new HashSet<>();
				Map<WatchKey, Path> keys = new HashMap<>();
				registerAll(watchService, alreadyWatchingDirs, keys, unpickDir);
				pollFileWatchEvents(watchService, alreadyWatchingDirs, keys);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, "Unpick directory watcher");
		thread.setDaemon(true);
		thread.start();
	}

	private static void registerAll(WatchService watchService, Set<Path> alreadyWatchingDirs, Map<WatchKey, Path> keys, Path dir) {
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<>() {
				@Override
				@NotNull
				public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
					Path realPath = dir.toRealPath();

					if (alreadyWatchingDirs.add(realPath)) {
						WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
						keys.put(key, realPath);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void pollFileWatchEvents(WatchService watchService, Set<Path> alreadyWatchingDirs, Map<WatchKey, Path> keys) {
		while (true) {
			WatchKey key;

			try {
				key = watchService.take();
			} catch (InterruptedException e) {
				return;
			}

			Path dir = keys.get(key);

			if (dir == null) {
				key.cancel();
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					continue;
				}

				Path filename = (Path) event.context();
				Path path = dir.resolve(filename);

				// if a file ending with .unpick is created, modified, or deleted, or if a directory is deleted, then
				// we need to refresh the unpick definitions
				if (filename.toString().endsWith(".unpick") || event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					unpickNeedsRefresh = true;
					SwingUtilities.invokeLater(() -> {
						if (unpickNeedsRefresh && isWindowFocused) {
							uninliner = null;
							unpickNeedsRefresh = false;

							if (project != null) {
								project.invalidateData(DataInvalidationEvent.InvalidationType.DECOMPILE);
							}
						}
					});
				} else if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
					registerAll(watchService, alreadyWatchingDirs, keys, path);
				}
			}

			if (!key.reset()) {
				keys.remove(key);
				alreadyWatchingDirs.remove(dir);
			}
		}
	}
}
