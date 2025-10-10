package net.fabricmc.filament.enigma.unpick;

import java.io.InputStream;

import cuchaz.enigma.api.service.I18nService;
import org.jetbrains.annotations.Nullable;

public class UnpickI18nService implements I18nService {
	@Override
	@Nullable
	public InputStream getTranslationResource(String language) {
		return UnpickI18nService.class.getResourceAsStream("/unpick_lang/" + language + ".json");
	}
}
