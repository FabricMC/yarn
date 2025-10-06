package net.fabricmc.filament.enigma.annotations;

import java.io.InputStream;

import cuchaz.enigma.api.service.I18nService;
import org.jetbrains.annotations.Nullable;

public class AnnotationsI18nService implements I18nService {
	@Override
	@Nullable
	public InputStream getTranslationResource(String language) {
		return AnnotationsI18nService.class.getResourceAsStream("/annotations_lang/" + language + ".json");
	}
}
