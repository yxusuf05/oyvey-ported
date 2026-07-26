package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.BuildConfig;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

/**
 * Ghost Client branding tag: a modern gradient panel with an animated accent underline that
 * gently pulses. This is the client's signature watermark.
 */
public class GhostTagHudModule extends HudModule {
    private static final String PRIMARY = "Ghost";
    private static final String SECONDARY = "Client";

    public final Setting<Boolean> version = bool("Version", true);
    public final Setting<Boolean> fps = bool("ShowFps", false);

    public GhostTagHudModule() {
        super("Ghost", "Animated Ghost Client watermark", 90, 16);
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);

        GuiGraphics context = e.getContext();
        float x = getX();
        float y = getY();
        float pad = 6f;

        Color accent = OyVey.colorManager.getColor();

        StringBuilder tail = new StringBuilder();
        if (version.getValue()) tail.append(" v").append(BuildConfig.VERSION);
        if (fps.getValue()) tail.append("  ").append(mc.getFps()).append(" fps");

        float primaryW = mc.font.width(PRIMARY);
        float secondaryW = mc.font.width(SECONDARY);
        float tailW = tail.length() > 0 ? mc.font.width(tail.toString()) : 0f;
        float width = pad * 2 + primaryW + 2f + secondaryW + tailW;
        float height = mc.font.lineHeight + 8f;

        // Panel with a subtle horizontal accent gradient.
        RenderUtil.rect(context, x, y, x + width, y + height, HudUtil.BACKGROUND.getRGB());
        RenderUtil.horizontalGradient(context, x, y, x + width, y + 1.5f,
                ColorUtil.withAlpha(accent, 200), ColorUtil.withAlpha(accent, 40));

        float textY = y + (height - mc.font.lineHeight) / 2f + 1f;
        float cursor = x + pad;
        context.drawString(mc.font, PRIMARY, (int) cursor, (int) textY, -1);
        cursor += primaryW + 2f;
        context.drawString(mc.font, SECONDARY, (int) cursor, (int) textY, ColorUtil.withAlpha(accent, 255).getRGB());
        cursor += secondaryW;
        if (tail.length() > 0) {
            context.drawString(mc.font, tail.toString(), (int) cursor, (int) textY, new Color(170, 170, 175).getRGB());
        }

        // Animated pulsing underline.
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 400.0) + 1.0) / 2.0);
        int alpha = (int) (60 + 150 * pulse);
        RenderUtil.rect(context, x, y + height - 1.5f, x + width, y + height,
                ColorUtil.withAlpha(accent, alpha).getRGB());

        setWidth(width);
        setHeight(height);
    }
}
