package me.alpha432.oyvey.features.sky;

import net.minecraft.util.Mth;

/**
 * Time based visibility of a sky layer, expressed in the daytime ticks Minecraft uses
 * (0 = 06:00, 6000 = 12:00, 12000 = 18:00, 18000 = 00:00).
 * <p>
 * All four points are treated as a ring so a layer may fade in in the evening and out
 * in the morning without any special casing.
 */
public record Fade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn) {
    public static final int DAY_LENGTH = 24000;
    public static final Fade ALWAYS = new Fade(0, 0, 0, 0, true);

    public static Fade of(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut) {
        return new Fade(wrap(startFadeIn), wrap(endFadeIn), wrap(startFadeOut), wrap(endFadeOut), false);
    }

    /**
     * Parses an OptiFine {@code hh:mm} time stamp into daytime ticks.
     *
     * @return the tick, or -1 when the input is not a valid time stamp
     */
    public static int parseTime(String time) {
        if (time == null) return -1;
        String[] parts = time.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return -1;
            // Minecraft's daytime 0 is 06:00
            return wrap(((hours - 6) * 1000) + ((minutes * 1000) / 60));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public float alphaAt(long dayTime) {
        if (this.alwaysOn) return 1.0f;
        int time = (int) Math.floorMod(dayTime, (long) DAY_LENGTH);
        if (inside(this.startFadeIn, this.endFadeIn, time)) {
            return progress(this.startFadeIn, this.endFadeIn, time);
        }
        if (inside(this.endFadeIn, this.startFadeOut, time)) {
            return 1.0f;
        }
        if (inside(this.startFadeOut, this.endFadeOut, time)) {
            return 1.0f - progress(this.startFadeOut, this.endFadeOut, time);
        }
        return 0.0f;
    }

    private static int wrap(int tick) {
        return Math.floorMod(tick, DAY_LENGTH);
    }

    /**
     * @return the amount of ticks between two daytime ticks, walking forwards over midnight
     */
    private static int span(int from, int to) {
        return Math.floorMod(to - from, DAY_LENGTH);
    }

    private static boolean inside(int from, int to, int time) {
        int length = span(from, to);
        return length != 0 && span(from, time) < length;
    }

    private static float progress(int from, int to, int time) {
        int length = span(from, to);
        if (length <= 0) return 1.0f;
        return Mth.clamp((float) span(from, time) / (float) length, 0.0f, 1.0f);
    }
}
