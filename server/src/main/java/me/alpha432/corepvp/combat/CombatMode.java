package me.alpha432.corepvp.combat;

import java.util.Locale;

/**
 * How combat feels for a kit.
 *
 * <p>Set per kit rather than server-wide, so classic 1.8-style kits and modern
 * crystal kits can live on the same server.
 */
public enum CombatMode {

    /**
     * 1.8 feel: the attack cooldown is removed by raising the attack speed
     * attribute, and sweep attacks are suppressed. What NoDebuff, Sumo and the
     * other classic kits expect.
     */
    LEGACY_1_8,

    /** Untouched vanilla 1.21 combat, cooldown and sweeping included. */
    MODERN,

    /**
     * Modern combat plus the crystal rules: fast crystal placement, explosion
     * damage attributed to whoever placed the crystal, and totem tracking.
     */
    CRYSTAL;

    public boolean legacy() {
        return this == LEGACY_1_8;
    }

    public boolean crystal() {
        return this == CRYSTAL;
    }

    public static CombatMode parse(String value, CombatMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
