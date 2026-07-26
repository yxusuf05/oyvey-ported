package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/**
 * Removes darkness by raising the client gamma. The original gamma is restored on disable.
 */
public class FullbrightModule extends Module {
    public final Setting<Double> gamma = num("Gamma", 15.0, 1.0, 100.0);

    private Double originalGamma;

    public FullbrightModule() {
        super("Fullbright", "Lets you see in the dark", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (mc.options == null) return;
        if (originalGamma == null) {
            originalGamma = mc.options.gamma().get();
        }
        mc.options.gamma().set(gamma.getValue());
    }

    @Override
    public void onDisable() {
        if (mc.options == null || originalGamma == null) return;
        mc.options.gamma().set(originalGamma);
        originalGamma = null;
    }
}
