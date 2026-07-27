package me.alpha432.oyvey.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.Stage;
import me.alpha432.oyvey.event.impl.entity.player.TickEvent;
import me.alpha432.oyvey.event.impl.entity.player.UpdateWalkingPlayerEvent;
import me.alpha432.oyvey.features.modules.combat.ReachModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

@Mixin(LocalPlayer.class)
public class MixinClientPlayerEntity {
    @ModifyExpressionValue(method = "raycastHitResult", at = @At(value = "INVOKE", target = "entityInteractionRange()D"))
    private double oyvey$entityReach(double original) {
        ReachModule reach = OyVey.moduleManager == null ? null : OyVey.moduleManager.getModuleByClass(ReachModule.class);
        return reach != null && reach.isEnabled() ? original + reach.entityReach.getValue() : original;
    }

    @ModifyExpressionValue(method = "raycastHitResult", at = @At(value = "INVOKE", target = "blockInteractionRange()D"))
    private double oyvey$blockReach(double original) {
        ReachModule reach = OyVey.moduleManager == null ? null : OyVey.moduleManager.getModuleByClass(ReachModule.class);
        return reach != null && reach.isEnabled() ? original + reach.blockReach.getValue() : original;
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void tickHook(CallbackInfo ci) {
        EVENT_BUS.post(new TickEvent());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER))
    private void tickHook2(CallbackInfo ci) {
        EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.PRE));
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V", shift = At.Shift.AFTER))
    private void tickHook3(CallbackInfo ci) {
        EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.POST));
    }
}
