package io.github.yxusuf05.skyloom.sky;

import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Set;

/**
 * A single layer of a sky. The texture is a skybox sheet in the layout OptiFine and
 * MCPatcher use: three columns by two rows, see {@link io.github.yxusuf05.skyloom.sky.render.SkyFace}.
 */
public final class SkyLayer {
    private final SkyTexture texture;
    private final BlendMode blend;
    private final boolean rotate;
    private final float speed;
    private final Vector3f axis;
    private final Fade fade;
    private final Set<WeatherCondition> weather;

    private SkyLayer(Builder builder) {
        this.texture = builder.texture;
        this.blend = builder.blend;
        this.rotate = builder.rotate;
        this.speed = builder.speed;
        this.axis = builder.axis;
        this.fade = builder.fade;
        this.weather = builder.weather;
    }

    public static Builder builder(SkyTexture texture) {
        return new Builder(texture);
    }

    public SkyTexture getTexture() {
        return this.texture;
    }

    public BlendMode getBlend() {
        return this.blend;
    }

    public boolean isRotating() {
        return this.rotate;
    }

    public float getSpeed() {
        return this.speed;
    }

    public Vector3f getAxis() {
        return this.axis;
    }

    /**
     * @return how visible this layer is right now, 0 when its conditions do not match
     */
    public float getAlpha(Level level) {
        if (!this.weather.contains(WeatherCondition.current(level))) return 0.0f;
        return this.fade.alphaAt(level.getDayTime());
    }

    public static class Builder {
        private final SkyTexture texture;
        private BlendMode blend = BlendMode.ADD;
        private boolean rotate = true;
        private float speed = 1.0f;
        private Vector3f axis = new Vector3f(0.0f, 0.0f, 1.0f);
        private Fade fade = Fade.ALWAYS;
        private Set<WeatherCondition> weather = EnumSet.allOf(WeatherCondition.class);

        private Builder(SkyTexture texture) {
            this.texture = texture;
        }

        public Builder blend(BlendMode blend) {
            this.blend = blend;
            return this;
        }

        public Builder rotate(boolean rotate) {
            this.rotate = rotate;
            return this;
        }

        public Builder speed(float speed) {
            this.speed = speed;
            return this;
        }

        public Builder axis(Vector3f axis) {
            if (axis.lengthSquared() > 1.0E-6f) this.axis = axis.normalize();
            return this;
        }

        public Builder fade(Fade fade) {
            this.fade = fade;
            return this;
        }

        public Builder weather(Set<WeatherCondition> weather) {
            if (!weather.isEmpty()) this.weather = EnumSet.copyOf(weather);
            return this;
        }

        public SkyLayer build() {
            return new SkyLayer(this);
        }
    }
}
