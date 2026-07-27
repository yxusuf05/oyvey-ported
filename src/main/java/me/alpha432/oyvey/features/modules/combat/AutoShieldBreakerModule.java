package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.inventory.Result;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.Vec3;

import static me.alpha432.oyvey.util.inventory.InventoryUtil.HOTBAR_SCOPE;

/**
 * When a nearby player raises their shield, silently swaps to an axe, hits them (an axe disables a
 * shield for 5 seconds) and swaps back. Only fires while the target is actually blocking.
 */
public class AutoShieldBreakerModule extends Module {
    public final Setting<Float> range = num("Range", 4.0f, 2.0f, 6.0f);
    public final Setting<Integer> delay = num("Delay", 100, 0, 1000);
    public final Setting<Boolean> rotate = bool("Rotate", true);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    private final Timer timer = new Timer();

    public AutoShieldBreakerModule() {
        super("AutoShieldBreaker", "Swaps to an axe to break a blocking enemy's shield", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.gameMode == null) return;
        if (!timer.passedMs(delay.getValue())) return;

        Player target = findBlockingTarget();
        if (target == null) return;

        Result axe = InventoryUtil.find(stack -> stack.getItem() instanceof AxeItem, HOTBAR_SCOPE);
        if (!axe.found()) return;

        if (rotate.getValue()) {
            float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), target.getEyePosition());
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    angles[0], angles[1], mc.player.onGround(), mc.player.horizontalCollision));
        }

        InventoryUtil.withSwap(axe, () -> {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
        timer.reset();
    }

    private Player findBlockingTarget() {
        double reach = range.getValue();
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !player.isBlocking()) continue;
            if (ignoreFriends.getValue() && OyVey.friendManager.isFriend(player)) continue;
            double distance = mc.player.getEyePosition().distanceTo(player.getEyePosition());
            if (distance > reach || distance >= bestDistance) continue;
            bestDistance = distance;
            best = player;
        }
        return best;
    }
}
