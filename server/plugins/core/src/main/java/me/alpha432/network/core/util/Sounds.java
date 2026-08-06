package me.alpha432.network.core.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Looks up sounds by name. Config files may use either the registry key
 * ({@code entity.player.levelup}) or the constant name ({@code ENTITY_PLAYER_LEVELUP}).
 */
public final class Sounds {

    private Sounds() {
    }

    public static Sound byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase());
        if (key != null) {
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
        }
        return legacy(trimmed);
    }

    // Constant names are the older style; Sound.valueOf is the only lookup that understands them.
    @SuppressWarnings({"deprecation", "removal"})
    private static Sound legacy(String raw) {
        try {
            return Sound.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Plays a configured sound for one player; silently does nothing when the name is empty. */
    public static void play(Player player, String raw, float volume, float pitch) {
        Sound sound = byName(raw);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static void playAt(Location location, String raw, float volume, float pitch) {
        Sound sound = byName(raw);
        if (sound != null && location.getWorld() != null) {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }
}
