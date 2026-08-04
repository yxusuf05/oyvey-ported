package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

/**
 * Shared drawing helpers that give every Ghost Client HUD element the same modern, flat
 * look: a translucent dark panel with a client-colored accent bar on the left.
 */
public final class HudUtil {
    public static final Color BACKGROUND = new Color(12, 12, 16, 150);

    private HudUtil() {
        throw new AssertionError("Can't create an instance of utility class");
    }

    public static void panel(GuiGraphics context, float x, float y, float width, float height) {
        RenderUtil.rect(context, x, y, x + width, y + height, BACKGROUND.getRGB());
        RenderUtil.rect(context, x, y, x + 2f, y + height, OyVey.colorManager.getColorAsIntFullAlpha());
    }
}
