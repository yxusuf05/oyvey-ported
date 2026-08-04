package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/**
 * Inflates the pick radius of living entities so your crosshair "catches" them a little more
 * easily. This only touches {@code Entity#getPickRadius}, which the game uses for ray-tracing —
 * not the collision box — so movement and physics are unaffected.
 *
 * <p>The expansion value is read from {@link me.alpha432.oyvey.mixin.entity.MixinEntity}.
 */
public class HitboxesModule extends Module {
    public final Setting<Float> expand = num("Expand", 0.1f, 0.05f, 0.4f);

    public HitboxesModule() {
        super("Hitboxes", "Enlarges entity hitboxes for easier aiming", Category.COMBAT);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("+%.2f", expand.getValue());
    }
}
