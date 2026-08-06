package me.alpha432.network.core.region;

import java.util.Locale;

/** What a {@link Region} allows or forbids. Every flag defaults to allowed. */
public enum RegionFlag {

    BUILD,
    PVP,
    DAMAGE,
    HUNGER,
    ITEM_DROP,
    ITEM_PICKUP,
    INTERACT,
    MOB_SPAWN,
    EXPLOSIONS;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static RegionFlag byKey(String key) {
        for (RegionFlag flag : values()) {
            if (flag.key().equalsIgnoreCase(key) || flag.name().equalsIgnoreCase(key)) {
                return flag;
            }
        }
        return null;
    }
}
