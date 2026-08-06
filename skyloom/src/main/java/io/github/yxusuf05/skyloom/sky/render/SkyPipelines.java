package io.github.yxusuf05.skyloom.sky.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.yxusuf05.skyloom.sky.BlendMode;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One pipeline per blend mode and one render type per (texture, blend mode) pair.
 * <p>
 * The pipelines mirror vanillas end sky pipeline, they only differ in the blend equation and
 * in having culling turned off so a sheet with an unusual winding still shows up.
 */
public final class SkyPipelines {
    private static final RenderPipeline.Snippet MATRICES_PROJECTION = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final Map<BlendMode, RenderPipeline> PIPELINES = new EnumMap<>(BlendMode.class);
    private static final Map<String, RenderType> RENDER_TYPES = new HashMap<>();

    private SkyPipelines() {
    }

    public static RenderType get(Identifier texture, BlendMode blend) {
        return RENDER_TYPES.computeIfAbsent(texture + "@" + blend.name(), key -> RenderType.create(
                "skyloom_sky_" + key,
                RenderSetup.builder(pipeline(blend)).withTexture("Sampler0", texture).createRenderSetup()
        ));
    }

    private static RenderPipeline pipeline(BlendMode blend) {
        return PIPELINES.computeIfAbsent(blend, mode -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(MATRICES_PROJECTION)
                    .withLocation(Identifier.fromNamespaceAndPath("skyloom", "pipeline/custom_sky_" + mode.name().toLowerCase(Locale.ROOT)))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
            if (mode.getFunction() == null) {
                builder.withoutBlend();
            } else {
                builder.withBlend(mode.getFunction());
            }
            return builder.build();
        });
    }
}
