package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Softly pulls your real camera toward the nearest target that is already close to your crosshair.
 * Unlike {@link SilentAimModule} this moves the visible view — a "legit"-style aim assist rather
 * than a silent snap. Only engages inside the {@code Fov} cone so it feels like assistance, not a
 * lock.
 */
public class AimAssistModule extends Module {
    public final Setting<Float> range = num("Range", 4.5f, 2.0f, 6.0f);
    public final Setting<Float> fov = num("Fov", 40.0f, 5.0f, 120.0f);
    public final Setting<Float> speed = num("Speed", 0.35f, 0.05f, 1.0f);
    public final Setting<Boolean> requireClick = bool("AttackKeyOnly", true);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", false);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    public AimAssistModule() {
        super("AimAssist", "Nudges your aim toward nearby targets", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (requireClick.getValue() && !mc.options.keyAttack.isDown()) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), target.getEyePosition());
        float deltaYaw = MathUtil.wrap(angles[0] - mc.player.getYRot());
        float deltaPitch = angles[1] - mc.player.getXRot();

        if (Math.abs(deltaYaw) > fov.getValue()) return;

        float factor = speed.getValue();
        mc.player.setYRot(mc.player.getYRot() + deltaYaw * factor);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + deltaPitch * factor, -90.0f, 90.0f));
    }

    private LivingEntity findTarget() {
        double reach = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(reach);
        LivingEntity best = null;
        double bestDelta = Double.MAX_VALUE;

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area, e -> e != mc.player && e.isAlive())) {
            boolean isPlayer = entity instanceof Player;
            if (isPlayer && !players.getValue()) continue;
            if (!isPlayer && !mobs.getValue()) continue;
            if (isPlayer && ignoreFriends.getValue() && OyVey.friendManager.isFriend((Player) entity)) continue;
            if (mc.player.getEyePosition().distanceTo(entity.getEyePosition()) > reach) continue;

            // Prefer the target whose direction is closest to where the crosshair already points.
            float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), entity.getEyePosition());
            double delta = Math.abs(MathUtil.wrap(angles[0] - mc.player.getYRot()));
            if (delta >= bestDelta) continue;
            bestDelta = delta;
            best = entity;
        }
        return best;
    }
}
