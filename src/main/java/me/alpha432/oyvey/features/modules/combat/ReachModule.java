package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/**
 * Extends how far the client will let you target entities (and, optionally, blocks). The extra
 * reach only stretches the client-side ray-trace; vanilla servers still validate attack distance,
 * so keep the entity value inside the server's tolerance (roughly +1 block) or hits get rejected.
 *
 * <p>The actual reach values are read from {@link me.alpha432.oyvey.mixin.entity.MixinClientPlayerEntity}.
 */
public class ReachModule extends Module {
    public final Setting<Double> entityReach = num("EntityReach", 0.5, 0.0, 1.5);
    public final Setting<Double> blockReach = num("BlockReach", 0.0, 0.0, 1.5);

    public ReachModule() {
        super("Reach", "Extends your entity (and block) interaction range", Category.COMBAT);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("+%.1f", entityReach.getValue());
    }
}
