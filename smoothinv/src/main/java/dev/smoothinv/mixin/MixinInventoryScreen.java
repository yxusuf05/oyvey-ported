package dev.smoothinv.mixin;

import dev.smoothinv.SmoothInvConfig;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Since 1.21.6 the player model in the inventory is rendered into its own
 * offscreen texture every frame, which is comparatively expensive —
 * especially with full enchanted armor. Optional because it visibly
 * removes the model from the screen.
 */
@Mixin(InventoryScreen.class)
public class MixinInventoryScreen {
    @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), cancellable = true)
    private static void smoothinv$skipPlayerModel(CallbackInfo ci) {
        if (SmoothInvConfig.get().hidePlayerModelInInventory) {
            ci.cancel();
        }
    }
}
