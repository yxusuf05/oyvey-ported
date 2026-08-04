package me.alpha432.oyvey.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.features.sky.render.CustomSkyRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void renderCustomSky(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                 MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        CustomSkyRenderer.render(poseStack, sunAngle);
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void hideSun(float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideSun()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void hideMoon(MoonPhase moonPhase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideMoon()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void hideStars(float brightness, PoseStack poseStack, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideStars()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void hideSunrise(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideSunrise()) ci.cancel();
    }
}
