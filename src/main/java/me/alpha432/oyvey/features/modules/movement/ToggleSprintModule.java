package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/**
 * Automatically sprints while moving forward so you never have to hold the sprint key.
 */
public class ToggleSprintModule extends Module {
    public final Setting<Boolean> keepSprint = bool("AllowBackwards", false);

    public ToggleSprintModule() {
        super("ToggleSprint", "Automatically sprints for you", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (mc.player.isUsingItem() || mc.player.isCrouching()) return;

        float forward = mc.player.input.getMoveVector().y;
        boolean moving = keepSprint.getValue() ? forward != 0f : forward > 0f;
        if (moving && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public String getDisplayInfo() {
        return keepSprint.getValue() ? "Omni" : "Forward";
    }
}
