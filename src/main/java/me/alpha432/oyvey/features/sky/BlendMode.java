package me.alpha432.oyvey.features.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;

/**
 * Blend equations of the OptiFine / MCPatcher custom sky format.
 * {@link #REPLACE} disables blending entirely, everything else maps onto the
 * source/destination factor pair the format documents for that name.
 */
public enum BlendMode {
    REPLACE(null),
    ALPHA(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA)),
    ADD(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE)),
    SUBTRACT(new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR)),
    MULTIPLY(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO)),
    DODGE(new BlendFunction(SourceFactor.ONE, DestFactor.ONE)),
    BURN(new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_COLOR)),
    SCREEN(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR)),
    OVERLAY(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR));

    private final BlendFunction function;

    BlendMode(BlendFunction function) {
        this.function = function;
    }

    /**
     * @return the blend function, or null when blending has to be turned off
     */
    public BlendFunction getFunction() {
        return this.function;
    }

    public static BlendMode byName(String name, BlendMode fallback) {
        if (name == null) return fallback;
        String trimmed = name.trim();
        for (BlendMode mode : values()) {
            if (mode.name().equalsIgnoreCase(trimmed)) return mode;
        }
        return fallback;
    }
}
