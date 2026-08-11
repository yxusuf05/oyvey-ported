package me.alpha432.corepvp.util;

import java.util.concurrent.TimeUnit;

public final class TimeUtil {

    private TimeUtil() {
    }

    /** {@code 83000 -> "1:23"}, used for match timers. */
    public static String clock(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    /** {@code 3725000 -> "1h 2m"}, used for "playing for" style text. */
    public static String compact(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24L;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + (seconds % 60L) + "s";
        }
        return seconds + "s";
    }

    /** One decimal place, for cooldown feedback like "2.4s". */
    public static String seconds(long millis) {
        return String.format("%.1fs", Math.max(0L, millis) / 1000.0D);
    }
}
