package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Sprint-reset ("W-tap"): the instant you attack a living entity, sprint is dropped for a couple of
 * ticks. Landing a hit on the first frame after a sprint reset gives the full sprint-knockback, so
 * this bumps your combo knockback without you having to tap the key by hand.
 */
public class WTapModule extends Module {
    public final Setting<Integer> ticks = num("Ticks", 1, 1, 5);

    private final Timer timer = new Timer();
    private boolean active;

    public WTapModule() {
        super("WTap", "Resets sprint on hit for extra knockback", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        active = false;
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof ServerboundInteractPacket packet
                && packet.action.getType() == ServerboundInteractPacket.ActionType.ATTACK) {
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof LivingEntity && !(entity instanceof EndCrystal)) {
                active = true;
                timer.reset();
            }
        }
    }

    @Override
    public void onTick() {
        if (nullCheck() || !active) return;
        if (timer.passedMs(ticks.getValue() * 50L)) {
            active = false;
            return;
        }
        mc.player.setSprinting(false);
    }
}
