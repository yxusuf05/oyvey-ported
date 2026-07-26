package me.alpha432.oyvey.features.modules.misc;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;

/**
 * Ghost Client performance module. Trades visual fidelity for frame-rate by toggling the
 * heaviest vanilla render options. Every original value is captured on enable and restored
 * on disable so nothing is lost permanently.
 */
public class FpsBoostModule extends Module {
    public final Setting<Boolean> reduceParticles = bool("ReduceParticles", true);
    public final Setting<Boolean> noShadows = bool("NoEntityShadows", true);
    public final Setting<Boolean> noClouds = bool("NoClouds", true);
    public final Setting<Boolean> fastGraphics = bool("FastGraphics", true);
    public final Setting<Boolean> noViewBobbing = bool("NoViewBobbing", false);
    public final Setting<Boolean> noSmoothLighting = bool("NoSmoothLighting", false);
    public final Setting<Boolean> limitRenderDistance = bool("LimitRenderDistance", false);
    public final Setting<Integer> maxRenderDistance = num("MaxRenderDistance", 8, 2, 16);
    public final Setting<Integer> biomeBlend = num("BiomeBlend", 0, 0, 7);
    public final Setting<Float> entityDistance = num("EntityDistance", 0.5f, 0.5f, 1.0f);

    // Captured vanilla values, restored on disable.
    private ParticleStatus originalParticles;
    private Boolean originalShadows;
    private CloudStatus originalClouds;
    private GraphicsPreset originalGraphics;
    private Boolean originalBobbing;
    private Boolean originalAo;
    private Integer originalBiomeBlend;
    private Double originalEntityDistance;
    private Integer originalRenderDistance;

    private boolean captured;
    private boolean graphicsApplied;
    private boolean renderDistanceApplied;

    public FpsBoostModule() {
        super("FpsBoost", "Boosts FPS by lowering expensive render settings", Category.MISC);
        maxRenderDistance.setVisibility(v -> limitRenderDistance.getValue());
    }

    @Override
    public void onEnable() {
        if (mc.options == null) return;
        capture();
        apply();
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void capture() {
        if (captured || mc.options == null) return;
        originalParticles = mc.options.particles().get();
        originalShadows = mc.options.entityShadows().get();
        originalClouds = mc.options.cloudStatus().get();
        originalGraphics = mc.options.graphicsPreset().get();
        originalBobbing = mc.options.bobView().get();
        originalAo = mc.options.ambientOcclusion().get();
        originalBiomeBlend = mc.options.biomeBlendRadius().get();
        originalEntityDistance = mc.options.entityDistanceScaling().get();
        originalRenderDistance = mc.options.renderDistance().get();
        captured = true;
    }

    private void apply() {
        if (mc.options == null) return;
        if (reduceParticles.getValue()) mc.options.particles().set(ParticleStatus.MINIMAL);
        if (noShadows.getValue()) mc.options.entityShadows().set(false);
        if (noClouds.getValue()) mc.options.cloudStatus().set(CloudStatus.OFF);
        if (noViewBobbing.getValue()) mc.options.bobView().set(false);
        if (noSmoothLighting.getValue()) mc.options.ambientOcclusion().set(false);
        mc.options.biomeBlendRadius().set(biomeBlend.getValue());
        mc.options.entityDistanceScaling().set(entityDistance.getValue().doubleValue());

        graphicsApplied = fastGraphics.getValue();
        if (graphicsApplied) {
            mc.options.applyGraphicsPreset(GraphicsPreset.FAST);
        }

        renderDistanceApplied = limitRenderDistance.getValue();
        if (renderDistanceApplied) {
            mc.options.renderDistance().set(Math.min(originalRenderDistance, maxRenderDistance.getValue()));
        }
        mc.options.save();
    }

    private void restore() {
        if (!captured || mc.options == null) return;
        mc.options.particles().set(originalParticles);
        mc.options.entityShadows().set(originalShadows);
        mc.options.cloudStatus().set(originalClouds);
        mc.options.bobView().set(originalBobbing);
        mc.options.ambientOcclusion().set(originalAo);
        mc.options.biomeBlendRadius().set(originalBiomeBlend);
        mc.options.entityDistanceScaling().set(originalEntityDistance);

        // Only reverse the settings that trigger a resource/chunk reload if we actually applied them.
        if (graphicsApplied) {
            mc.options.applyGraphicsPreset(originalGraphics);
            graphicsApplied = false;
        }
        if (renderDistanceApplied) {
            mc.options.renderDistance().set(originalRenderDistance);
            renderDistanceApplied = false;
        }
        mc.options.save();
        captured = false;
    }
}
