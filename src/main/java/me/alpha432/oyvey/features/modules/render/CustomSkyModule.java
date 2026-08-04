package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.gui.CustomSkyScreen;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.features.sky.SkyPack;
import me.alpha432.oyvey.features.sky.SkyRegistry;
import org.lwjgl.glfw.GLFW;

/**
 * Custom skies. The keybind opens the picker, the picked sky keeps rendering after the
 * picker is closed and survives restarts because it is stored as a setting.
 */
public class CustomSkyModule extends Module {
    private static CustomSkyModule INSTANCE;

    public final Setting<String> sky = str("Sky", SkyRegistry.NONE);
    public final Setting<Float> brightness = num("Brightness", 1.0f, 0.0f, 1.0f);
    public final Setting<Boolean> rotate = bool("Rotate", true);
    public final Setting<Float> speed = num("Speed", 1.0f, 0.0f, 5.0f);
    public final Setting<Boolean> hideSun = bool("HideSun", false);
    public final Setting<Boolean> hideMoon = bool("HideMoon", false);
    public final Setting<Boolean> hideStars = bool("HideStars", false);
    public final Setting<Boolean> overworldOnly = bool("OverworldOnly", true);

    public CustomSkyModule() {
        super("CustomSky", "Opens the custom sky picker", Category.RENDER);
        setBind(GLFW.GLFW_KEY_I);
        // the sky is picked in the screen, showing it as an editable text field would only confuse
        this.sky.setVisibility(value -> false);
        this.speed.setVisibility(value -> this.rotate.getValue());
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        mc.setScreen(CustomSkyScreen.getInstance());
    }

    @Override
    public void onTick() {
        if (!(mc.screen instanceof CustomSkyScreen)) this.disable();
    }

    @Override
    public String getDisplayInfo() {
        SkyPack pack = SkyRegistry.byId(this.sky.getValue());
        return pack == null ? null : pack.getName();
    }

    public static CustomSkyModule getInstance() {
        return INSTANCE;
    }
}
