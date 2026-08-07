package me.alpha432.network.practice.bot;

import java.util.Locale;

/** How the bot moves and whether it fights back. */
public enum BotBehavior {

    /** Closes in and attacks whenever it can reach you. */
    AGGRESSIVE(true),
    /** Keeps its distance, backs off when you get close and only counters. */
    DEFENSIVE(true),
    /** Circles around you without ever attacking — pure aim and combo training. */
    STRAFE(false),
    /** Pushes you around without dealing damage, like a sumo opponent. */
    SUMO(false),
    /** Attacks, but the damage is ignored: only the hit count matters. */
    BOXING(true);

    private final boolean attacks;

    BotBehavior(boolean attacks) {
        this.attacks = attacks;
    }

    public boolean attacks() {
        return attacks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BotBehavior byName(String name) {
        for (BotBehavior behavior : values()) {
            if (behavior.name().equalsIgnoreCase(name)) {
                return behavior;
            }
        }
        return null;
    }
}
