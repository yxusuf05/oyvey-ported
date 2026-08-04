package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.DamageUtil;
import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Crystal PvP bot: picks a target, searches the surrounding obsidian/bedrock for the placement that
 * deals the most damage, places a crystal there and detonates it — while refusing any action that
 * would blow up more of its own health than it is allowed to lose.
 *
 * <p>Damage numbers come from {@link DamageUtil}, which reproduces the vanilla explosion pipeline,
 * so the "is this worth it" decisions match what the server will actually do. The candidate search
 * is gated behind a cheap distance-only upper bound so the expensive ray-traced exposure is only
 * paid for positions that could clear the damage threshold.
 */
public class AutoCrystalModule extends Module {
    public final Setting<Boolean> place = bool("Place", true);
    public final Setting<Boolean> breakCrystals = bool("Break", true);
    public final Setting<Integer> placeDelay = num("PlaceDelay", 50, 0, 500);
    public final Setting<Integer> breakDelay = num("BreakDelay", 50, 0, 500);

    public final Setting<Float> targetRange = num("TargetRange", 12.0f, 1.0f, 20.0f);
    public final Setting<Float> placeRange = num("PlaceRange", 4.5f, 1.0f, 6.0f);
    public final Setting<Float> breakRange = num("BreakRange", 4.5f, 1.0f, 6.0f);
    public final Setting<Float> wallRange = num("WallRange", 3.5f, 0.0f, 6.0f);

    public final Setting<Float> minDamage = num("MinDamage", 6.0f, 0.5f, 20.0f);
    public final Setting<Float> minBreakDamage = num("MinBreakDamage", 1.5f, 0.0f, 20.0f);
    public final Setting<Float> maxSelfDamage = num("MaxSelfDamage", 8.0f, 0.0f, 20.0f);
    public final Setting<Float> minHealth = num("MinHealth", 6.0f, 0.0f, 20.0f);
    public final Setting<Float> facePlaceHealth = num("FacePlaceHealth", 8.0f, 0.0f, 20.0f);

    public final Setting<Boolean> rotate = bool("Rotate", true);
    public final Setting<Boolean> autoSwap = bool("AutoSwap", true);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);
    public final Setting<Boolean> pauseOnScreen = bool("PauseOnScreen", false);

    public final Setting<Boolean> render = bool("Render", true);
    public final Setting<Color> renderColor = color("RenderColor", 170, 0, 255, 255);

    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();

    private BlockPos renderPos;
    private LivingEntity target;

    public AutoCrystalModule() {
        super("AutoCrystal", "Places and detonates end crystals on the best target", Category.COMBAT);
        placeDelay.setVisibility(v -> place.getValue());
        breakDelay.setVisibility(v -> breakCrystals.getValue());
        renderColor.setVisibility(v -> render.getValue());
    }

    @Override
    public void onDisable() {
        renderPos = null;
        target = null;
    }

    @Override
    public void onTick() {
        renderPos = null;
        target = null;
        if (nullCheck() || mc.gameMode == null) return;
        if (pauseOnScreen.getValue() && mc.screen != null) return;
        if (DamageUtil.effectiveHealth(mc.player) <= minHealth.getValue()) return;

        target = findTarget();
        if (target == null) return;

        if (breakCrystals.getValue() && breakTimer.passedMs(breakDelay.getValue())) {
            if (detonate(target)) breakTimer.reset();
        }
        if (place.getValue() && placeTimer.passedMs(placeDelay.getValue())) {
            placeBest(target);
        }
    }

    // ------------------------------------------------------------------ targeting

    private LivingEntity findTarget() {
        double range = targetRange.getValue();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.getValue() && OyVey.friendManager.isFriend(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range || distance >= bestDistance) continue;
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    // ------------------------------------------------------------------ breaking

    private boolean detonate(LivingEntity target) {
        double range = breakRange.getValue();
        AABB area = mc.player.getBoundingBox().inflate(range);

        EndCrystal best = null;
        float bestDamage = 0.0f;

        for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, area, EndCrystal::isAlive)) {
            Vec3 center = crystal.position();
            if (!inReach(center, range)) continue;

            float selfDamage = DamageUtil.crystalDamage(center, mc.player);
            if (!selfDamageAcceptable(selfDamage)) continue;

            float damage = DamageUtil.crystalDamage(center, target);
            if (damage < requiredDamage(target, minBreakDamage.getValue())) continue;
            if (damage <= bestDamage) continue;

            bestDamage = damage;
            best = crystal;
        }

        if (best == null) return false;

        if (rotate.getValue()) lookAt(best.getBoundingBox().getCenter());
        mc.gameMode.attack(mc.player, best);
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    // ------------------------------------------------------------------ placing

    private void placeBest(LivingEntity target) {
        // Only check availability here — the actual hand swap happens once a position is chosen,
        // so we never yank crystals into the hotbar for a placement that never happens.
        if (!hasCrystal()) return;

        double range = placeRange.getValue();
        BlockPos origin = target.blockPosition();
        int horizontal = (int) Math.ceil(range);

        BlockPos best = null;
        float bestDamage = 0.0f;
        float required = requiredDamage(target, minDamage.getValue());

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-horizontal, -3, -horizontal),
                origin.offset(horizontal, 3, horizontal))) {

            Vec3 center = crystalCenter(pos);
            if (!inReach(Vec3.atCenterOf(pos), range)) continue;
            // Cheap distance-only bound first: skip before paying for exposure ray-tracing.
            if (DamageUtil.maxPossibleDamage(center, target, DamageUtil.CRYSTAL_POWER) < required) continue;
            if (!canPlaceCrystal(pos)) continue;

            float damage = DamageUtil.crystalDamage(center, target);
            if (damage < required || damage <= bestDamage) continue;
            if (!selfDamageAcceptable(DamageUtil.crystalDamage(center, mc.player))) continue;

            bestDamage = damage;
            best = pos.immutable();
        }

        if (best == null) return;
        renderPos = best;

        InteractionHand hand = crystalHand();
        if (hand == null) return;

        if (rotate.getValue()) lookAt(Vec3.atCenterOf(best));
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand,
                new BlockHitResult(Vec3.atCenterOf(best), Direction.UP, best, false));
        if (result instanceof InteractionResult.Success success
                && success.swingSource() != InteractionResult.SwingSource.NONE) {
            mc.player.connection.send(new ServerboundSwingPacket(hand));
        }
        placeTimer.reset();
    }

    /**
     * Mirrors {@code EndCrystalItem#useOn}: obsidian or bedrock, an empty block above it and
     * nothing standing in that block.
     */
    private boolean canPlaceCrystal(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) return false;

        BlockPos above = pos.above();
        if (!mc.level.isEmptyBlock(above)) return false;

        AABB box = new AABB(above.getX(), above.getY(), above.getZ(),
                above.getX() + 1.0, above.getY() + 1.0, above.getZ() + 1.0);
        return mc.level.getEntities((Entity) null, box).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    /** Explosion origin of a crystal placed on {@code pos} — it spawns one block above it. */
    private Vec3 crystalCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    /** Damage threshold, relaxed to a face-place once the target is nearly dead. */
    private float requiredDamage(LivingEntity target, float configured) {
        return DamageUtil.effectiveHealth(target) <= facePlaceHealth.getValue() ? 0.5f : configured;
    }

    private boolean selfDamageAcceptable(float selfDamage) {
        if (selfDamage > maxSelfDamage.getValue()) return false;
        return DamageUtil.effectiveHealth(mc.player) - selfDamage > minHealth.getValue();
    }

    /** Full reach when the position is visible, reduced reach when we would be shooting through blocks. */
    private boolean inReach(Vec3 pos, double range) {
        double distance = mc.player.getEyePosition().distanceTo(pos);
        if (distance > range) return false;
        return visible(pos) || distance <= wallRange.getValue();
    }

    private boolean visible(Vec3 pos) {
        HitResult hit = mc.level.clip(new ClipContext(mc.player.getEyePosition(), pos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Sends the rotation to the server without moving the on-screen camera, so aiming at a crystal
     * does not yank the player's view around.
     */
    private void lookAt(Vec3 pos) {
        float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), pos);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                angles[0], angles[1], mc.player.onGround(), mc.player.horizontalCollision));
    }

    /** Whether a crystal is reachable at all, without changing the held item. */
    private boolean hasCrystal() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return true;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return true;
        if (!autoSwap.getValue()) return false;

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)) return true;
        }
        return false;
    }

    /** Off-hand first, then main hand, then a hotbar swap when {@code AutoSwap} is on. */
    private InteractionHand crystalHand() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return InteractionHand.OFF_HAND;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return InteractionHand.MAIN_HAND;
        if (!autoSwap.getValue()) return null;

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)) {
                InventoryUtil.swap(slot);
                return InteractionHand.MAIN_HAND;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ render

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || renderPos == null) return;
        AABB box = new AABB(renderPos.getX(), renderPos.getY() + 1.0, renderPos.getZ(),
                renderPos.getX() + 1.0, renderPos.getY() + 2.0, renderPos.getZ() + 1.0);
        RenderUtil.drawBoxFilled(event.getMatrix(), box, ColorUtil.withAlpha(renderColor.getValue(), 60));
        RenderUtil.drawBox(event.getMatrix(), box, renderColor.getValue(), 1.5f);
    }

    @Override
    public String getDisplayInfo() {
        return target == null ? "None" : target.getName().getString();
    }
}
