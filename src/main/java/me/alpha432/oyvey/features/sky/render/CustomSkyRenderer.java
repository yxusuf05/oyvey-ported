package me.alpha432.oyvey.features.sky.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.CustomSkyModule;
import me.alpha432.oyvey.features.sky.SkyLayer;
import me.alpha432.oyvey.features.sky.SkyPack;
import me.alpha432.oyvey.features.sky.SkyRegistry;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
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
public final class CustomSkyRenderer implements Util {
    private CustomSkyRenderer() {
    }

    /**
     * @return the sky that should be drawn in the level the player is in, or null
     */
    public static SkyPack getRenderedSky() {
        CustomSkyModule module = getModule();
        if (module == null || mc.level == null) return null;
        if (module.overworldOnly.getValue() && mc.level.dimension() != Level.OVERWORLD) return null;
        return SkyRegistry.getActive();
    }

    public static boolean shouldHideSun() {
        CustomSkyModule module = getModule();
        return module != null && module.hideSun.getValue() && getRenderedSky() != null;
    }

    public static boolean shouldHideMoon() {
        CustomSkyModule module = getModule();
        return module != null && module.hideMoon.getValue() && getRenderedSky() != null;
    }

    public static boolean shouldHideStars() {
        CustomSkyModule module = getModule();
        return module != null && module.hideStars.getValue() && getRenderedSky() != null;
    }

    /**
     * @param poseStack the pose vanilla hands to the celestial bodies, world aligned around the camera
     * @param sunAngle  the suns angle in radians, used to spin the sky along with the day
     */
    public static void render(PoseStack poseStack, float sunAngle) {
        SkyPack pack = getRenderedSky();
        CustomSkyModule module = getModule();
        if (pack == null || module == null) return;

        ClientLevel level = mc.level;
        float brightness = Mth.clamp(module.brightness.getValue(), 0.0f, 1.0f);
        if (brightness <= 0.0f) return;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(poseStack.last().pose());

        for (SkyLayer layer : pack.getLayers()) {
            float alpha = layer.getAlpha(level);
            if (alpha <= 0.001f) continue;

            modelView.pushMatrix();
            if (layer.isRotating() && module.rotate.getValue()) {
                Vector3f axis = layer.getAxis();
                float angle = sunAngle * layer.getSpeed() * module.speed.getValue();
                modelView.rotate(new Quaternionf().rotationAxis(angle, axis.x(), axis.y(), axis.z()));
            }
            drawCube(layer, ARGB.colorFromFloat(alpha, brightness, brightness, brightness));
            modelView.popMatrix();
        }

        modelView.popMatrix();
    }

    private static void drawCube(SkyLayer layer, int color) {
        RenderType type = SkyPipelines.get(layer.getTexture(), layer.getBlend());
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

    private static CustomSkyModule getModule() {
        if (OyVey.moduleManager == null) return null;
        return OyVey.moduleManager.getModuleByClass(CustomSkyModule.class);
    }
}
