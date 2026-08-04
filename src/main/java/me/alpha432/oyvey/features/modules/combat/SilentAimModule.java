package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.Stage;
import me.alpha432.oyvey.event.impl.entity.player.UpdateWalkingPlayerEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Silently rotates toward the nearest target for the outgoing movement packet only, then restores
 * your real view — so your hits register on the server while your camera never moves. Hooks the
 * existing {@code sendPosition} window via {@link UpdateWalkingPlayerEvent}; pairs with manual
 * clicking (or {@link TriggerBotModule}).
 */
public class SilentAimModule extends Module {
    public final Setting<Float> range = num("Range", 4.5f, 2.0f, 6.0f);
    public final Setting<Boolean> attackOnly = bool("AttackKeyOnly", true);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", false);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    public SilentAimModule() {
        super("SilentAim", "Silently aims at targets for your hits to land", Category.COMBAT);
    }

    @Subscribe
    public void onUpdateWalking(UpdateWalkingPlayerEvent event) {
        if (nullCheck()) return;
        if (attackOnly.getValue() && !mc.options.keyAttack.isDown()) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        if (event.getStage() == Stage.PRE) {
            OyVey.rotationManager.updateRotations();
            float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), target.getEyePosition());
            OyVey.rotationManager.setPlayerRotations(angles[0], angles[1]);
        } else if (event.getStage() == Stage.POST) {
            OyVey.rotationManager.restoreRotations();
        }
    }

    private LivingEntity findTarget() {
        double reach = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(reach);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area, e -> e != mc.player && e.isAlive())) {
            boolean isPlayer = entity instanceof Player;
            if (isPlayer && !players.getValue()) continue;
            if (!isPlayer && !mobs.getValue()) continue;
            if (isPlayer && ignoreFriends.getValue() && OyVey.friendManager.isFriend((Player) entity)) continue;

            double distance = mc.player.getEyePosition().distanceTo(entity.getEyePosition());
            if (distance > reach || distance >= bestDistance) continue;
            bestDistance = distance;
            best = entity;
        }
        return best;
    }
}
