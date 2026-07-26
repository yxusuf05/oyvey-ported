package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.awt.Color;
import java.util.Collection;

public class PotionHudModule extends HudModule {
    private static final Color TIME_COLOR = new Color(170, 170, 175);

    public final Setting<Boolean> background = bool("Background", true);

    public PotionHudModule() {
        super("PotionHud", "Displays your active potion effects", 90, 20);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "";
            case 2 -> " II";
            case 3 -> " III";
            case 4 -> " IV";
            case 5 -> " V";
            default -> " " + value;
        };
    }

    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "**:**";
        int seconds = effect.getDuration() / 20;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);
        if (nullCheck()) return;

        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        GuiGraphics context = e.getContext();
        float x = getX();
        float y = getY();

        if (effects.isEmpty()) {
            setWidth(80);
            setHeight(mc.font.lineHeight + 4f);
            return;
        }

        float lineHeight = mc.font.lineHeight + 2f;
        float height = effects.size() * lineHeight + 4f;

        float maxWidth = 0f;
        int i = 0;
        for (MobEffectInstance effect : effects) {
            String name = effect.getEffect().value().getDisplayName().getString() + roman(effect.getAmplifier() + 1);
            String time = duration(effect);
            float w = mc.font.width(name) + 8f + mc.font.width(time) + 12f;
            maxWidth = Math.max(maxWidth, w);
            i++;
        }

        if (background.getValue()) {
            HudUtil.panel(context, x, y, maxWidth, height);
        }

        i = 0;
        for (MobEffectInstance effect : effects) {
            String name = effect.getEffect().value().getDisplayName().getString() + roman(effect.getAmplifier() + 1);
            String time = duration(effect);
            float lineY = y + 3f + i * lineHeight;
            context.drawString(mc.font, name, (int) (x + 6f), (int) lineY, -1);
            context.drawString(mc.font, time,
                    (int) (x + maxWidth - mc.font.width(time) - 6f), (int) lineY, TIME_COLOR.getRGB());
            i++;
        }

        setWidth(maxWidth);
        setHeight(height);
    }
}
