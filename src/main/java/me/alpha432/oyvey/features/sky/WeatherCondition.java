package me.alpha432.oyvey.features.sky;

import net.minecraft.world.level.Level;

public enum WeatherCondition {
    CLEAR,
    RAIN,
    THUNDER;

    public static WeatherCondition current(Level level) {
        if (level.isThundering()) return THUNDER;
        if (level.isRaining()) return RAIN;
        return CLEAR;
    }

    public static WeatherCondition byName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        for (WeatherCondition condition : values()) {
            if (condition.name().equalsIgnoreCase(trimmed)) return condition;
        }
        return null;
    }
}
