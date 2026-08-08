package io.github.yxusuf05.skyloom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yxusuf05.skyloom.sky.render.CustomSkyRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 1.21.9 and 1.21.10 shape of the sky hooks. Two things differ from 1.21.11: the moon phase is
 * a plain int rather than an enum, and the day is handed over as a fraction of a turn instead of
 * as an angle in radians.
 */
@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {
    private static final float FULL_TURN = (float) (Math.PI * 2.0);

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void skyloom$renderSky(PoseStack poseStack, float timeOfDay, int moonPhase, float rainBrightness,
                                   float starBrightness, CallbackInfo ci) {
        CustomSkyRenderer.render(poseStack, timeOfDay * FULL_TURN);
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideSun(float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideSun()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideMoon(int moonPhase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideMoon()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideStars(float brightness, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideStars()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideSunrise(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideSunrise()) ci.cancel();
    }
}
