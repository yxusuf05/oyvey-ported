package io.github.yxusuf05.skyloom.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import io.github.yxusuf05.skyloom.sky.render.CustomSkyRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideClouds(FrameGraphBuilder frameGraphBuilder, CloudStatus cloudStatus, Vec3 cameraPosition,
                                    long ticks, float partialTick, int color, float height, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideClouds()) ci.cancel();
    }

    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true)
    private void skyloom$hideWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (CustomSkyRenderer.shouldHideWeather()) ci.cancel();
    }
}
