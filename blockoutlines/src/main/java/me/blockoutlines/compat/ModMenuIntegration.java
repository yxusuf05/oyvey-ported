package me.blockoutlines.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.blockoutlines.gui.ConfigScreen;

/**
 * Entry point Mod Menu looks for. Only loaded when Mod Menu is installed, so the mod itself
 * keeps working without it.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
