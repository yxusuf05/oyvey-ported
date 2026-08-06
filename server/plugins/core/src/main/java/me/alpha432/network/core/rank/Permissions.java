package me.alpha432.network.core.rank;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Helper for tiered permissions such as {@code network.smp.homes.10}, where the number behind
 * the last dot is the actual limit. The highest granted number wins.
 */
public final class Permissions {

    private Permissions() {
    }

    public static int highest(Permissible permissible, String prefix, int fallback) {
        int best = fallback;
        for (PermissionAttachmentInfo info : permissible.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission();
            if (!permission.startsWith(prefix)) {
                continue;
            }
            String suffix = permission.substring(prefix.length());
            if (suffix.equals("*")) {
                continue;
            }
            try {
                best = Math.max(best, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // Not a numeric tier, skip it.
            }
        }
        return best;
    }

    public static double highestPercent(Permissible permissible, String prefix) {
        return highest(permissible, prefix, 0) / 100.0D;
    }
}
