package net.fabricmc.filament.task.minecraft;

import java.net.URISyntaxException;

import javax.inject.Inject;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.filament.task.base.FileOutputTask;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadException;

public abstract class DownloadMinecraftJarTask extends FileOutputTask {
	@Input
	public abstract Property<MinecraftVersionMeta> getVersionMeta();

	@Input
	public abstract Property<String> getSide();

	@Inject
	public DownloadMinecraftJarTask() {
		getVersionMeta().finalizeValueOnRead();
		getSide().finalizeValueOnRead();
	}

	@TaskAction
	public void run() throws URISyntaxException, DownloadException {
		final MinecraftVersionMeta.Download download = getVersionMeta().get().download(getSide().get());

		Download.create(download.url())
				.sha1(download.sha1())
				.downloadPath(getOutputFile().getAsFile().get().toPath());
	}
}
