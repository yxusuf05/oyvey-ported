package me.blockoutlines.mixin;

import me.blockoutlines.BlockOutlines;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void blockoutlines$keyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }
        BlockOutlines.onKeyPressed(keyEvent.key());
    }
}
