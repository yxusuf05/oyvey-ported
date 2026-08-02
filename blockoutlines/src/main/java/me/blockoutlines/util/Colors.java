package me.blockoutlines.util;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class Colors {
    private Colors() {
    }

    /** Replaces the alpha channel of an ARGB colour. */
    public static int withAlpha(int argb, int alpha) {
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(argb), ARGB.green(argb), ARGB.blue(argb));
    }

    /** Scales the alpha channel of an ARGB colour by a factor between 0 and 1. */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(ARGB.alpha(argb) * Mth.clamp(factor, 0.0f, 1.0f)));
    }

    /**
     * A colour cycling through the hue circle, keeping the alpha of {@code argb}.
     *
     * @param cyclesPerMinute how many full rainbow cycles happen in a minute
     * @param offset          phase shift in cycles, used to give every block its own hue
     */
    public static int rainbow(int argb, float cyclesPerMinute, float offset) {
        float period = 60_000.0f / Math.max(1.0f, cyclesPerMinute);
        float hue = ((System.currentTimeMillis() % (long) period) / period + offset) % 1.0f;
        return Mth.hsvToArgb(hue, 0.85f, 1.0f, ARGB.alpha(argb));
    }

    public static String toHex(int argb) {
        return String.format("%08X", argb);
    }

    /**
     * Parses {@code RRGGBB}, {@code AARRGGBB} or either of them prefixed with {@code #}.
     *
     * @return the parsed colour or {@code fallback} when the text is not a valid colour
     */
    public static int parseHex(String text, int fallback) {
        String cleaned = text.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        try {
            if (cleaned.length() == 6) {
                return 0xFF000000 | Integer.parseInt(cleaned, 16);
            }
            if (cleaned.length() == 8) {
                return (int) Long.parseLong(cleaned, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through to the fallback below
        }
        return fallback;
    }
}
