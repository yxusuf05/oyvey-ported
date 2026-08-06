package me.alpha432.network.core.text;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Replaces {@code %placeholder%} tokens in configurable text. Percent signs are used so the
 * tokens never collide with MiniMessage tags.
 */
public final class Placeholders {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Placeholders() {
    }

    public static String apply(Player player, String raw) {
        if (raw == null || raw.indexOf('%') < 0) {
            return raw;
        }
        Rank rank = Core.ranks().of(player);
        PlayerProfile profile = Core.profiles().get(player);
        LocalDateTime now = LocalDateTime.now();

        String result = raw;
        result = result.replace("%player%", player.getName());
        result = result.replace("%displayname%", player.getName());
        result = result.replace("%world%", player.getWorld().getName());
        result = result.replace("%ping%", String.valueOf(player.getPing()));
        result = result.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        result = result.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        result = result.replace("%rank%", rank.id());
        result = result.replace("%rank_display%", rank.displayName());
        result = result.replace("%rank_prefix%", rank.prefix());
        result = result.replace("%rank_suffix%", rank.suffix());
        result = result.replace("%rank_color%", rank.nameColor());
        result = result.replace("%balance%", Core.economy().format(profile == null ? 0 : profile.balance()));
        result = result.replace("%playtime%", formatDuration(profile == null ? 0 : profile.playTime()));
        result = result.replace("%tps%", String.format("%.1f", Bukkit.getTPS()[0]));
        result = result.replace("%date%", now.format(DATE));
        result = result.replace("%time%", now.format(TIME));
        result = result.replace("%x%", String.valueOf(player.getLocation().getBlockX()));
        result = result.replace("%y%", String.valueOf(player.getLocation().getBlockY()));
        result = result.replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
        return result;
    }

    public static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    /** Turns seconds into {@code 1m 05s} for cooldown messages. */
    public static String formatSeconds(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + String.format("%02ds", seconds % 60);
    }
}
