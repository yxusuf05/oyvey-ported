package me.alpha432.corepvp.storage;

import java.util.Locale;

public enum StorageType {

    /** One file, no setup, fine for a single server. */
    SQLITE,
    /** For when several servers share one database. */
    MYSQL;

    public static StorageType parse(String value, StorageType fallback) {
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
