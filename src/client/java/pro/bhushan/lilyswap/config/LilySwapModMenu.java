package pro.bhushan.lilyswap.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded only when Mod Menu is installed (via the "modmenu" entrypoint). */
public class LilySwapModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LilySwapConfigScreen::new;
	}
}
