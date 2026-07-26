package me.alpha432.oyvey.util;

import me.alpha432.oyvey.features.modules.client.ClickGuiModule;

import java.awt.*;

public class ColorUtil {
    public static Color rainbow(int delay) {
        double rainbowState = Math.ceil((double) (System.currentTimeMillis() + (long) delay) / 20.0);
        return Color.getHSBColor((float) ((rainbowState % 360.0) / 360.0), ClickGuiModule.getInstance().rainbowSaturation.getValue() / 255.0f, ClickGuiModule.getInstance().rainbowBrightness.getValue() / 255.0f);
    }

    /**
     * Linearly interpolates between two colors including their alpha channel.
     *
     * @param t progress in the range [0, 1]
     */
    public static Color interpolate(Color a, Color b, float t) {
        t = t < 0f ? 0f : Math.min(t, 1f);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(clamp(r), clamp(g), clamp(bl), clamp(al));
    }

    /**
     * Returns the given color with its alpha replaced.
     */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}