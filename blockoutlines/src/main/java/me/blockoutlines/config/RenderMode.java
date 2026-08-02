package me.blockoutlines.config;

public enum RenderMode {
    OUTLINE("Outline"),
    FILL("Fill"),
    BOTH("Outline + Fill");

    private final String displayName;

    RenderMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean outline() {
        return this == OUTLINE || this == BOTH;
    }

    public boolean fill() {
        return this == FILL || this == BOTH;
    }
}
