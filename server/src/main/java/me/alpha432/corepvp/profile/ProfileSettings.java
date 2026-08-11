package me.alpha432.corepvp.profile;

import java.util.EnumMap;
import java.util.Map;

/** The toggles a player controls from the settings menu. */
public final class ProfileSettings {

    public enum Flag {
        SCOREBOARD("opt_scoreboard", true),
        DUEL_REQUESTS("opt_duel_requests", true),
        PARTY_INVITES("opt_party_invites", true),
        PLAYER_VISIBILITY("opt_player_visible", true),
        ALLOW_SPECTATORS("opt_allow_spectate", true);

        private final String column;
        private final boolean defaultValue;

        Flag(String column, boolean defaultValue) {
            this.column = column;
            this.defaultValue = defaultValue;
        }

        public String column() {
            return column;
        }

        public boolean defaultValue() {
            return defaultValue;
        }
    }

    private final Map<Flag, Boolean> values = new EnumMap<>(Flag.class);

    public ProfileSettings() {
        for (Flag flag : Flag.values()) {
            values.put(flag, flag.defaultValue());
        }
    }

    public boolean get(Flag flag) {
        return values.getOrDefault(flag, flag.defaultValue());
    }

    public void set(Flag flag, boolean value) {
        values.put(flag, value);
    }

    public boolean toggle(Flag flag) {
        boolean next = !get(flag);
        set(flag, next);
        return next;
    }
}
