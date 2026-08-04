package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;

/**
 * Holds the forward key for you until disabled. Handy for AFK travel; releases the key cleanly on
 * disable so you are not left walking.
 */
public class AutoWalkModule extends Module {
    public AutoWalkModule() {
        super("AutoWalk", "Automatically walks forward", Category.MOVEMENT);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.keyUp.setDown(false);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        mc.options.keyUp.setDown(true);
    }
}
