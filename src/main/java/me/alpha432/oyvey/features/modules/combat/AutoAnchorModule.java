package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.DamageUtil;
import me.alpha432.oyvey.util.InteractionUtil;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.inventory.Result;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static me.alpha432.oyvey.util.inventory.InventoryUtil.FULL_SCOPE;
import static me.alpha432.oyvey.util.inventory.InventoryUtil.HOTBAR_SCOPE;

/**
 * Respawn-anchor combat (overworld / end): finds or places an anchor next to the target, charges it
 * with glowstone and detonates it. Every step is guarded by the same {@link DamageUtil} predictions
 * as {@link AutoCrystalModule}, so it will not blow itself up past {@code MaxSelfDamage}.
 *
 * <p>{@code SafeAnchor} flips the intent: instead of detonating, it stops at one charge so an anchor
 * you rely on for movement/utility never explodes in your face.
 */
public class AutoAnchorModule extends Module {
    /** Respawn-anchor explosion power (RespawnAnchorBlock#explode). */
    private static final float ANCHOR_POWER = 5.0f;

    public final Setting<Boolean> place = bool("Place", true);
    public final Setting<Boolean> charge = bool("Charge", true);
    public final Setting<Boolean> explode = bool("Explode", true);
    public final Setting<Boolean> safeAnchor = bool("SafeAnchor", false);

    public final Setting<Integer> delay = num("Delay", 100, 0, 1000);
    public final Setting<Float> targetRange = num("TargetRange", 8.0f, 1.0f, 20.0f);
    public final Setting<Float> reach = num("Reach", 4.5f, 1.0f, 6.0f);

    public final Setting<Float> minDamage = num("MinDamage", 6.0f, 0.5f, 20.0f);
    public final Setting<Float> maxSelfDamage = num("MaxSelfDamage", 9.0f, 0.0f, 20.0f);
    public final Setting<Float> minHealth = num("MinHealth", 6.0f, 0.0f, 20.0f);

    public final Setting<Boolean> useInventory = bool("UseInventory", false);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);
    public final Setting<Boolean> render = bool("Render", true);
    public final Setting<Color> renderColor = color("RenderColor", 255, 90, 0, 255);

    private final Timer timer = new Timer();
    private BlockPos renderPos;

    public AutoAnchorModule() {
        super("AutoAnchor", "Places, charges and detonates respawn anchors", Category.COMBAT);
        explode.setVisibility(v -> !safeAnchor.getValue());
    }

    @Override
    public void onDisable() {
        renderPos = null;
    }

    @Override
    public void onTick() {
        renderPos = null;
        if (nullCheck() || mc.gameMode == null) return;
        if (!timer.passedMs(delay.getValue())) return;
        if (DamageUtil.effectiveHealth(mc.player) <= minHealth.getValue()) return;

        Player target = findTarget();
        if (target == null) return;

        BlockPos anchor = findAnchor(target);
        if (anchor == null && place.getValue()) {
            anchor = placeAnchor(target);
            if (anchor != null) {
                renderPos = anchor;
                timer.reset();
            }
            return; // give the placement a tick to register before charging
        }
        if (anchor == null) return;
        renderPos = anchor;

        BlockState state = mc.level.getBlockState(anchor);
        int chargeLevel = state.getValue(RespawnAnchorBlock.CHARGE);

        if (chargeLevel < 1) {
            if (charge.getValue()) chargeAnchor(anchor);
            return;
        }

        if (safeAnchor.getValue() || !explode.getValue()) return;

        Vec3 center = Vec3.atCenterOf(anchor);
        float damage = DamageUtil.explosionDamage(center, target, ANCHOR_POWER);
        if (damage < requiredDamage(target)) return;

        float selfDamage = DamageUtil.explosionDamage(center, mc.player, ANCHOR_POWER);
        if (selfDamage > maxSelfDamage.getValue()) return;
        if (DamageUtil.effectiveHealth(mc.player) - selfDamage <= minHealth.getValue()) return;

        detonate(anchor);
        timer.reset();
    }

    // ------------------------------------------------------------------ steps

    private void chargeAnchor(BlockPos anchor) {
        Result glowstone = InventoryUtil.find(Items.GLOWSTONE, useInventory.getValue() ? FULL_SCOPE : HOTBAR_SCOPE);
        if (!glowstone.found()) return;
        InventoryUtil.withSwap(glowstone, () -> InteractionUtil.useItem(anchor, glowstone.hand()));
    }

    private void detonate(BlockPos anchor) {
        // Detonating requires interacting with a non-glowstone hand; otherwise it would just charge.
        if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            Result other = InventoryUtil.find(stack -> !stack.isEmpty() && !stack.is(Items.GLOWSTONE), HOTBAR_SCOPE);
            if (other.found()) {
                InventoryUtil.withSwap(other, () -> InteractionUtil.useItem(anchor, other.hand()));
                return;
            }
        }
        InteractionUtil.useItem(anchor);
    }

    private BlockPos placeAnchor(Player target) {
        Result anchorItem = InventoryUtil.find(Items.RESPAWN_ANCHOR, useInventory.getValue() ? FULL_SCOPE : HOTBAR_SCOPE);
        if (!anchorItem.found()) return null;

        BlockPos best = null;
        float bestDamage = 0.0f;
        float required = requiredDamage(target);
        for (BlockPos pos : candidatePositions(target)) {
            Vec3 center = Vec3.atCenterOf(pos);
            float damage = DamageUtil.explosionDamage(center, target, ANCHOR_POWER);
            if (damage < required || damage <= bestDamage) continue;

            float selfDamage = DamageUtil.explosionDamage(center, mc.player, ANCHOR_POWER);
            if (selfDamage > maxSelfDamage.getValue()) continue;
            if (DamageUtil.effectiveHealth(mc.player) - selfDamage <= minHealth.getValue()) continue;

            bestDamage = damage;
            best = pos;
        }
        if (best == null) return null;

        BlockPos placed = best;
        InventoryUtil.withSwap(anchorItem, () -> InteractionUtil.place(placed, false, anchorItem.hand()));
        return best;
    }

    // ------------------------------------------------------------------ search

    private List<BlockPos> candidatePositions(Player target) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos feet = target.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (!InteractionUtil.isPlaceable(pos, true)) continue;
                    if (!inReach(pos)) continue;
                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    private BlockPos findAnchor(Player target) {
        double range = reach.getValue();
        BlockPos feet = target.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int r = (int) Math.ceil(range) + 2;

        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -r, -r), feet.offset(r, r, r))) {
            if (!mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) continue;
            if (!inReach(pos)) continue;
            double distance = target.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = pos.immutable();
        }
        return best;
    }

    private Player findTarget() {
        double range = targetRange.getValue();
        Player best = null;
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

    // ------------------------------------------------------------------ helpers

    private float requiredDamage(Player target) {
        return DamageUtil.effectiveHealth(target) <= 8.0f ? 0.5f : minDamage.getValue();
    }

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= reach.getValue();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || renderPos == null) return;
        AABB box = new AABB(renderPos);
        RenderUtil.drawBoxFilled(event.getMatrix(), box, ColorUtil.withAlpha(renderColor.getValue(), 60));
        RenderUtil.drawBox(event.getMatrix(), box, renderColor.getValue(), 1.5f);
    }
}
