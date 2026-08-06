package io.github.yxusuf05.skyloom.sky.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.yxusuf05.skyloom.Skyloom;
import io.github.yxusuf05.skyloom.SkyloomConfig;
import io.github.yxusuf05.skyloom.sky.SkyLayer;
import io.github.yxusuf05.skyloom.sky.SkyPack;
import io.github.yxusuf05.skyloom.sky.SkyRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the selected sky as a cube around the camera, from inside vanillas sky pass so that
 * everything behind it (the sky colour) is already there and everything after it (sun, moon,
 * stars, clouds, terrain) still lands on top.
 */
public final class CustomSkyRenderer {
    private CustomSkyRenderer() {
    }

    /**
     * @return the sky that should be drawn in the level the player is in, or null
     */
    public static SkyPack getRenderedSky() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (Skyloom.config().overworldOnly && level.dimension() != Level.OVERWORLD) return null;
        return SkyRegistry.getActive();
    }

    public static boolean shouldHideSun() {
        return Skyloom.config().hideSun;
    }

    public static boolean shouldHideMoon() {
        return Skyloom.config().hideMoon;
    }

    public static boolean shouldHideStars() {
        return Skyloom.config().hideStars;
    }

    public static boolean shouldHideSunrise() {
        return Skyloom.config().hideSunrise;
    }

    public static boolean shouldHideClouds() {
        return Skyloom.config().hideClouds;
    }

    public static boolean shouldHideWeather() {
        return Skyloom.config().hideWeather;
    }

    /**
     * @param poseStack the pose vanilla hands to the celestial bodies, world aligned around the camera
     * @param sunAngle  the suns angle in radians, used to spin the sky along with the day
     */
    public static void render(PoseStack poseStack, float sunAngle) {
        SkyPack pack = getRenderedSky();
        if (pack == null) return;

        SkyloomConfig config = Skyloom.config();
        ClientLevel level = Minecraft.getInstance().level;
        float brightness = Mth.clamp(config.brightness, 0.0f, 1.0f);
        if (brightness <= 0.0f) return;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(poseStack.last().pose());

        for (SkyLayer layer : pack.getLayers()) {
            float alpha = layer.getAlpha(level);
            if (alpha <= 0.001f) continue;

            Identifier texture = layer.getTexture().resolve();
            if (texture == null) continue; // still decoding, the vanilla sky carries this frame

            modelView.pushMatrix();
            if (layer.isRotating() && config.rotate) {
                Vector3f axis = layer.getAxis();
                float angle = sunAngle * layer.getSpeed() * config.speed;
                modelView.rotate(new Quaternionf().rotationAxis(angle, axis.x(), axis.y(), axis.z()));
            }
            drawCube(texture, layer, ARGB.colorFromFloat(alpha, brightness, brightness, brightness));
            modelView.popMatrix();
        }

        modelView.popMatrix();
    }

    private static void drawCube(Identifier texture, SkyLayer layer, int color) {
        RenderType type = SkyPipelines.get(texture, layer.getBlend());
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (SkyFace face : SkyFace.values()) {
            Matrix4f matrix = face.getMatrix();
            float size = SkyFace.SIZE;
            builder.addVertex(matrix, -size, -size, -size).setUv(face.getMinU(), face.getMinV()).setColor(color);
            builder.addVertex(matrix, -size, -size, size).setUv(face.getMinU(), face.getMaxV()).setColor(color);
            builder.addVertex(matrix, size, -size, size).setUv(face.getMaxU(), face.getMaxV()).setColor(color);
            builder.addVertex(matrix, size, -size, -size).setUv(face.getMaxU(), face.getMinV()).setColor(color);
        }

        MeshData mesh = builder.build();
        if (mesh != null) type.draw(mesh);
    }
}
