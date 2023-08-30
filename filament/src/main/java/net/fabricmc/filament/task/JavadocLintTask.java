package net.fabricmc.filament.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileType;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import net.fabricmc.filament.util.FileUtil;

public abstract class JavadocLintTask extends DefaultTask {
	private static final Pattern PARAM_DOC_LINE = Pattern.compile("^@param\\s+[^<].*$");
	private final DirectoryProperty mappingDirectory = getProject().getObjects().directoryProperty();

	@Incremental
	@InputDirectory
	public DirectoryProperty getMappingDirectory() {
		return mappingDirectory;
	}

	public JavadocLintTask() {
		// Ignore outputs for up-to-date checks as there aren't any (so only inputs are checked)
		getOutputs().upToDateWhen(task -> true);
	}

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run(InputChanges changes) {
		List<FileChange> fileChanges = new ArrayList<>();
		changes.getFileChanges(mappingDirectory).forEach(fileChanges::add);

		if (fileChanges.isEmpty()) {
			// Nothing changed, nothing to do!
			return;
		}

		WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(LintAction.class, parameters -> {
			for (FileChange change : fileChanges) {
				if (change.getChangeType() != ChangeType.REMOVED && change.getFileType() == FileType.FILE) {
					parameters.getMappingFiles().from(change.getFile());
				}
			}
		});
	}

	private static boolean isRegularMethodParameter(String line) {
		return PARAM_DOC_LINE.matcher(line).matches();
	}

	private static String getFirstWord(String str) {
		int i = str.indexOf(' ');
		return i != -1 ? str.substring(0, i) : str;
	}

	private static String getFullName(EntryTree<EntryMapping> mappings, Entry<?> entry) {
		String name = mappings.get(entry).targetName();

		if (entry instanceof MethodEntry method) {
			name += method.getDesc().toString();
		}

		if (entry.getParent() != null) {
			name = getFullName(mappings, entry.getParent()) + '.' + name;
		}

		return name;
	}

	public interface LintParameters extends WorkParameters {
		ConfigurableFileCollection getMappingFiles();
	}

	public abstract static class LintAction implements WorkAction<LintParameters> {
		private static final Logger LOGGER = Logging.getLogger(LintAction.class);

		@Inject
		public LintAction() {
		}

		@Override
		public void execute() {
			try {
				Path[] files = FileUtil.toPaths(getParameters().getMappingFiles().getFiles()).toArray(new Path[0]);
				EntryTree<EntryMapping> mappings = EnigmaMappingsReader.readFiles(ProgressListener.none(), files);
				List<String> errors = new ArrayList<>();

				mappings.getAllEntries().parallel().forEach(entry -> {
					EntryMapping mapping = mappings.get(entry);
					String javadoc = mapping.javadoc();

					if (javadoc != null && !javadoc.isEmpty()) {
						List<String> localErrors = new ArrayList<>();

						if (entry instanceof LocalVariableEntry && ((LocalVariableEntry) entry).isArgument()) {
							if (javadoc.endsWith(".")) {
								localErrors.add("parameter javadoc ends with '.'");
							}

							if (Character.isUpperCase(javadoc.charAt(0))) {
								String word = getFirstWord(javadoc);

								// ignore single-letter "words" (like X or Z)
								if (word.length() > 1) {
									localErrors.add("parameter javadoc starts with uppercase word '" + word + "'");
								}
							}
						} else if (entry instanceof MethodEntry) {
							if (javadoc.lines().anyMatch(JavadocLintTask::isRegularMethodParameter)) {
								localErrors.add("method javadoc contains parameter docs, which should be on the parameter itself");
							}
						}

						// new rules can be added here in the future

						if (!localErrors.isEmpty()) {
							String name = getFullName(mappings, entry);

							for (String error : localErrors) {
								errors.add(name + ": " + error);
							}
						}
					}
				});

				if (!errors.isEmpty()) {
					for (String error : errors) {
						LOGGER.error("lint: {}", error);
					}

					throw new GradleException("Found " + errors.size() + " javadoc format errors! See the log for details.");
				}
			} catch (IOException | MappingParseException e) {
				throw new GradleException("Could not read and parse mappings", e);
			}
		}
	}
}
