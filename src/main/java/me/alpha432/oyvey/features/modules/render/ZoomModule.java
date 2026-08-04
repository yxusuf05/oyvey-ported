package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.AnimationUtil;

/**
 * Toggleable zoom. Smoothly interpolates the field of view toward a divided target while
 * active and restores the original FOV on disable.
 */
public class ZoomModule extends Module {
    public final Setting<Float> zoom = num("Divider", 4.0f, 1.5f, 10.0f);

    private final AnimationUtil.Animation animation = new AnimationUtil.Animation(70f, 14f);
    private Integer originalFov;

    public ZoomModule() {
        super("Zoom", "Zooms your view in", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (mc.options == null) return;
        originalFov = mc.options.fov().get();
        animation.setValue(originalFov);
        animation.setTarget(originalFov / zoom.getValue());
    }

    @Override
    public void onDisable() {
        if (mc.options == null || originalFov == null) return;
        mc.options.fov().set(originalFov);
        originalFov = null;
    }

    @Override
    public void onTick() {
        if (mc.options == null || originalFov == null) return;
        animation.setTarget(originalFov / zoom.getValue());
        int fov = Math.round(animation.update());
        mc.options.fov().set(Math.max(1, fov));
    }
}
