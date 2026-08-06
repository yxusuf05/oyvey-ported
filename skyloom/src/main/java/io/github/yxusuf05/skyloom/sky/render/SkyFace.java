package io.github.yxusuf05.skyloom.sky.render;

import org.joml.Matrix4f;

/**
 * The six sides of the skybox cube together with their place on the texture sheet.
 * <p>
 * OptiFine and MCPatcher lay the sheet out as three columns by two rows:
 * <pre>
 *     +--------+--------+--------+
 *     | bottom |  top   |  east  |
 *     +--------+--------+--------+
 *     | south  |  west  |  north |
 *     +--------+--------+--------+
 * </pre>
 * Every side is the same quad, spanning the full width and depth at the bottom of the cube,
 * spun into place by its own matrix. This is how vanilla builds the end sky as well.
 */
public enum SkyFace {
    BOTTOM(new Matrix4f().rotateY(halfPi()), 0, 0),
    TOP(new Matrix4f().rotateX((float) Math.PI).rotateY(-halfPi()), 1, 0),
    EAST(new Matrix4f().rotateX(halfPi()).rotateZ(halfPi()), 2, 0),
    SOUTH(new Matrix4f().rotateX(halfPi()).rotateZ((float) Math.PI), 0, 1),
    WEST(new Matrix4f().rotateX(halfPi()).rotateZ(-halfPi()), 1, 1),
    NORTH(new Matrix4f().rotateX(halfPi()), 2, 1);

    public static final float SIZE = 100.0f;
    private static final float COLUMN = 1.0f / 3.0f;
    private static final float ROW = 1.0f / 2.0f;

    private final Matrix4f matrix;
    private final float minU;
    private final float minV;

    SkyFace(Matrix4f matrix, int column, int row) {
        this.matrix = matrix;
        this.minU = column * COLUMN;
        this.minV = row * ROW;
    }

    private static float halfPi() {
        return (float) (Math.PI / 2.0);
    }

    public Matrix4f getMatrix() {
        return this.matrix;
    }

    public float getMinU() {
        return this.minU;
    }

    public float getMaxU() {
        return this.minU + COLUMN;
    }

    public float getMinV() {
        return this.minV;
    }

    public float getMaxV() {
        return this.minV + ROW;
    }
}
