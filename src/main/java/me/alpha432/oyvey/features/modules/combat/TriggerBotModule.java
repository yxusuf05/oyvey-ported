package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Automatically attacks the entity your crosshair is over, respecting a configurable delay and
 * target filters. Skips friends and never fires while a screen (inventory, chat) is open.
 */
public class TriggerBotModule extends Module {
    public final Setting<Integer> delay = num("Delay", 120, 0, 1000);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", true);
    public final Setting<Boolean> crystals = bool("Crystals", false);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    private final Timer timer = new Timer();

    public TriggerBotModule() {
        super("TriggerBot", "Attacks the entity you look at", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.screen != null || mc.gameMode == null) return;
        if (!(mc.hitResult instanceof EntityHitResult hit)) return;

        Entity target = hit.getEntity();
        if (target == mc.player) return;

        if (target instanceof Player player) {
            if (!players.getValue()) return;
            if (ignoreFriends.getValue() && OyVey.friendManager.isFriend(player)) return;
        } else if (target instanceof EndCrystal) {
            if (!crystals.getValue()) return;
        } else if (target instanceof LivingEntity) {
            if (!mobs.getValue()) return;
        } else {
            return;
        }

        if (!timer.passedMs(delay.getValue())) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        timer.reset();
    }

    @Override
    public String getDisplayInfo() {
        return delay.getValue() + "ms";
    }
}
