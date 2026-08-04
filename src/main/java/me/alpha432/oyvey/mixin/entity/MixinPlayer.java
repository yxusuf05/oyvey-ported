package me.alpha432.oyvey.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.combat.ReachModule;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static me.alpha432.oyvey.util.traits.Util.mc;

/**
 * Reach: extends the local player's interaction range by modifying the return of
 * {@code entityInteractionRange}/{@code blockInteractionRange}. Targeting the methods themselves
 * (rather than a call site) keeps the mixin refmap-independent, and the {@code == mc.player} guard
 * makes sure only your own reach is stretched.
 */
@Mixin(Player.class)
public class MixinPlayer {
    @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
    private double oyvey$entityReach(double original) {
        return (Object) this == mc.player ? original + reach(true) : original;
    }

    @ModifyReturnValue(method = "blockInteractionRange", at = @At("RETURN"))
    private double oyvey$blockReach(double original) {
        return (Object) this == mc.player ? original + reach(false) : original;
    }

    private double reach(boolean entity) {
        if (OyVey.moduleManager == null) return 0.0;
        ReachModule module = OyVey.moduleManager.getModuleByClass(ReachModule.class);
        if (module == null || !module.isEnabled()) return 0.0;
        return entity ? module.entityReach.getValue() : module.blockReach.getValue();
    }
}
