package io.github.yxusuf05.skyloom.sky.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.yxusuf05.skyloom.sky.BlendMode;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.21.9 and 1.21.10 shape of the render types. The pipelines are built exactly as on 1.21.11,
 * only the wrapper around them still goes through CompositeState instead of RenderSetup.
 */
public final class SkyPipelines {
    /** Matches vanillas sky buffers, a cube of six quads never needs more. */
    private static final int BUFFER_SIZE = 1536;

    private static final RenderPipeline.Snippet MATRICES_PROJECTION = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final Map<BlendMode, RenderPipeline> PIPELINES = new EnumMap<>(BlendMode.class);
    private static final Map<String, RenderType> RENDER_TYPES = new HashMap<>();

    private SkyPipelines() {
    }

    public static RenderType get(ResourceLocation texture, BlendMode blend) {
        return RENDER_TYPES.computeIfAbsent(texture + "@" + blend.name(), key -> RenderType.create(
                "skyloom_sky_" + key,
                BUFFER_SIZE,
                pipeline(blend),
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                        .createCompositeState(false)
        ));
    }

    private static RenderPipeline pipeline(BlendMode blend) {
        return PIPELINES.computeIfAbsent(blend, mode -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(MATRICES_PROJECTION)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("skyloom", "pipeline/custom_sky_" + mode.name().toLowerCase(Locale.ROOT)))
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
