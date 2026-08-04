package me.alpha432.oyvey.util;

/**
 * Small collection of easing functions and a self-contained, frame-rate independent
 * animator used by the Ghost Client HUD elements to keep transitions smooth regardless
 * of the user's FPS.
 */
public final class AnimationUtil {
    private AnimationUtil() {
        throw new AssertionError("Can't create an instance of utility class");
    }

    public static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    public static float lerp(float start, float end, float t) {
        return start + (end - start) * clamp01(t);
    }

    public static double lerp(double start, double end, double t) {
        return start + (end - start) * Math.max(0.0, Math.min(t, 1.0));
    }

    public static float easeOutQuad(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float f = 1f - t;
        return 1f - f * f * f;
    }

    public static float easeOutExpo(float t) {
        t = clamp01(t);
        return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
    }

    /**
     * Stateful exponential smoother. Each instance tracks its own wall-clock timing so it
     * converges toward {@link #target} at the same real-world rate on any machine, from a
     * potato at 30 FPS to a 500 FPS rig.
     */
    public static final class Animation {
        private float value;
        private float target;
        private float speed;
        private long lastNanos = System.nanoTime();

        /**
         * @param initial starting value
         * @param speed   convergence rate per second (higher = snappier)
         */
        public Animation(float initial, float speed) {
            this.value = initial;
            this.target = initial;
            this.speed = speed;
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public float getTarget() {
            return target;
        }

        public float getValue() {
            return value;
        }

        public void setValue(float value) {
            this.value = value;
        }

        public float update() {
            long now = System.nanoTime();
            float dt = (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            // Guard against huge gaps (window focus loss, GC pause) that would snap the value.
            if (dt > 0.1f) dt = 0.1f;
            value += (target - value) * clamp01(speed * dt);
            return value;
        }
    }
}
