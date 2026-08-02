package me.blockoutlines.config;

public enum TargetMode {
    VANILLA("Vanilla"),
    CUSTOM("Custom"),
    HIDDEN("Hidden");

    private final String displayName;

    TargetMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
