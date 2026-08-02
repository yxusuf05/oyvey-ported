package me.blockoutlines.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.blockoutlines.BlockOutlines;
import me.blockoutlines.config.TargetMode;
import me.blockoutlines.render.HighlightRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    /**
     * Runs once per frame while the level renderer extracts its state, which is inside the
     * scope of the per-frame gizmo collector - exactly where custom world geometry belongs.
     * Injected at HEAD on purpose: the method returns early whenever the crosshair is not on
     * a block, so a TAIL injection would only fire while looking at something.
     */
    @Inject(method = "extractBlockOutline", at = @At("HEAD"))
    private void blockoutlines$emitOutlines(Camera camera, LevelRenderState levelRenderState, CallbackInfo ci) {
        HighlightRenderer.emit();
    }

    /** Suppresses the vanilla black outline whenever the mod draws its own (or hides it). */
    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void blockoutlines$hideVanillaOutline(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack,
                                                  boolean translucentPass, LevelRenderState levelRenderState,
                                                  CallbackInfo ci) {
        if (BlockOutlines.config().enabled && BlockOutlines.config().targetMode != TargetMode.VANILLA) {
            ci.cancel();
        }
    }
}
