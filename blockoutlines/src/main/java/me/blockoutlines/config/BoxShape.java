package me.blockoutlines.config;

public enum BoxShape {
    BLOCK_SHAPE("Block shape"),
    FULL_CUBE("Full cube");

    private final String displayName;

    BoxShape(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
