package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;

public class FpsHudModule extends HudModule {
    public final Setting<Boolean> background = bool("Background", true);
    public final Setting<Boolean> label = bool("ShowLabel", true);

    public FpsHudModule() {
        super("FPS", "Displays your current FPS", 40, 12);
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);

        String text = mc.getFps() + (label.getValue() ? " FPS" : "");
        float pad = 5f;
        float height = mc.font.lineHeight + 5f;
        float width = mc.font.width(text) + pad + 5f;

        float x = getX();
        float y = getY();

        GuiGraphics context = e.getContext();
        if (background.getValue()) {
            HudUtil.panel(context, x, y, width, height);
        }
        context.drawString(mc.font, text,
                (int) (x + pad), (int) (y + (height - mc.font.lineHeight) / 2f + 1), -1);

        setWidth(width);
        setHeight(height);
    }
}
