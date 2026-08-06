package me.alpha432.network.staff;

import java.util.concurrent.TimeUnit;

/** Parses and prints durations like {@code 7d}, {@code 12h30m} or {@code 90s}. */
public final class Durations {

    private Durations() {
    }

    /**
     * @return the duration in milliseconds, or -1 when the input cannot be read.
     *         {@code perm}, {@code permanent} and {@code 0} mean permanent and return 0.
     */
    public static long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        String input = raw.trim().toLowerCase();
        if (input.equals("perm") || input.equals("permanent") || input.equals("0")) {
            return 0L;
        }
        long total = 0L;
        long number = 0L;
        boolean sawDigit = false;
        boolean sawUnit = false;
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                number = number * 10 + (c - '0');
                sawDigit = true;
                continue;
            }
            if (!sawDigit) {
                return -1L;
            }
            long unit = switch (c) {
                case 's' -> TimeUnit.SECONDS.toMillis(1);
                case 'm' -> TimeUnit.MINUTES.toMillis(1);
                case 'h' -> TimeUnit.HOURS.toMillis(1);
                case 'd' -> TimeUnit.DAYS.toMillis(1);
                case 'w' -> TimeUnit.DAYS.toMillis(7);
                case 'y' -> TimeUnit.DAYS.toMillis(365);
                default -> -1L;
            };
            if (unit < 0) {
                return -1L;
            }
            total += number * unit;
            number = 0L;
            sawDigit = false;
            sawUnit = true;
        }
        // A bare number without a unit is not accepted; it is too easy to misread.
        return sawUnit && !sawDigit ? total : -1L;
    }

    /** Human readable remainder, e.g. {@code 2d 4h 15m}. */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append("d ");
        }
        if (hours > 0) {
            text.append(hours).append("h ");
        }
        if (minutes > 0) {
            text.append(minutes).append("m ");
        }
        if (text.isEmpty() || (days == 0 && hours == 0 && seconds > 0)) {
            text.append(seconds).append("s");
        }
        return text.toString().trim();
    }
}
