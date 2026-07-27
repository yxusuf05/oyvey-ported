package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Melee combat aura: attacks the best living entity in reach. Rotations are sent as movement packets
 * so the on-screen camera stays where you point it, and (optionally) it waits for the vanilla attack
 * cooldown so every hit lands for full damage.
 */
public class KillAuraModule extends Module {
    public final Setting<Float> range = num("Range", 4.0f, 2.0f, 6.0f);
    public final Setting<Integer> delay = num("Delay", 50, 0, 500);
    public final Setting<Boolean> requireFullCharge = bool("RequireFullCharge", true);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", false);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);
    public final Setting<Boolean> rotate = bool("Rotate", true);
    public final Setting<Boolean> pauseOnScreen = bool("PauseOnScreen", true);

    private final Timer timer = new Timer();
    private LivingEntity target;

    public KillAuraModule() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @Override
    public void onTick() {
        target = null;
        if (nullCheck() || mc.gameMode == null) return;
        if (pauseOnScreen.getValue() && mc.screen != null) return;

        target = findTarget();
        if (target == null) return;
        if (!timer.passedMs(delay.getValue())) return;
        if (requireFullCharge.getValue() && mc.player.getAttackStrengthScale(0.0f) < 1.0f) return;

        if (rotate.getValue()) lookAt(target.getEyePosition());
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        timer.reset();
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

    private void lookAt(Vec3 pos) {
        float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), pos);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                angles[0], angles[1], mc.player.onGround(), mc.player.horizontalCollision));
    }

    @Override
    public String getDisplayInfo() {
        return target == null ? "None" : target.getName().getString();
    }
}
