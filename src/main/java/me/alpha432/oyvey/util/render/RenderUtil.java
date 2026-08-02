package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import me.alpha432.oyvey.util.render.state.RectRenderState;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3x2f;

import java.awt.*;

public class RenderUtil implements Util {

    public static void rect(GuiGraphics context, float x1, float y1, float x2, float y2, int color) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);
        context.fill(ix1, iy1, ix2, iy2, color);
    }

    public static void rect(GuiGraphics context, float x1, float y1, float x2, float y2, int color, float width) {
        int w = Math.max(1, Math.round(width));
        context.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y1) + w, color);
        context.fill(Math.round(x2) - w, Math.round(y1), Math.round(x2), Math.round(y2), color);
        context.fill(Math.round(x1), Math.round(y2) - w, Math.round(x2), Math.round(y2), color);
        context.fill(Math.round(x1), Math.round(y1), Math.round(x1) + w, Math.round(y2), color);
    }

    /**
     * Filled rectangle with rounded corners, drawn entirely with {@link GuiGraphics#fill} so it
     * works on the 1.21 GUI render pipeline without any custom shader. Regions never overlap, so
     * translucent colours blend cleanly (no double-blended seams).
     */
    public static void roundedRect(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int color) {
        roundedRect(g, x1, y1, x2, y2, radius, color, true, true);
    }

    public static void roundedRectTop(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int color) {
        roundedRect(g, x1, y1, x2, y2, radius, color, true, false);
    }

    public static void roundedRectBottom(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int color) {
        roundedRect(g, x1, y1, x2, y2, radius, color, false, true);
    }

    private static void roundedRect(GuiGraphics g, float fx1, float fy1, float fx2, float fy2, float radius, int color, boolean roundTop, boolean roundBottom) {
        int x1 = Math.round(fx1), y1 = Math.round(fy1), x2 = Math.round(fx2), y2 = Math.round(fy2);
        if (x2 < x1) { int t = x1; x1 = x2; x2 = t; }
        if (y2 < y1) { int t = y1; y1 = y2; y2 = t; }

        int r = Math.round(radius);
        int maxR = Math.min((x2 - x1) / 2, (y2 - y1) / 2);
        if (r > maxR) r = maxR;
        if (r <= 0) { g.fill(x1, y1, x2, y2, color); return; }

        int top = roundTop ? r : 0;
        int bottom = roundBottom ? r : 0;

        g.fill(x1, y1 + top, x2, y2 - bottom, color);
        for (int i = 0; i < top; i++) {
            int inset = cornerInset(r, i);
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
        }
        for (int i = 0; i < bottom; i++) {
            int inset = cornerInset(r, i);
            g.fill(x1 + inset, y2 - 1 - i, x2 - inset, y2 - i, color);
        }
    }

    private static int cornerInset(int r, int row) {
        double dv = (r - row) - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0.0, r * r - dv * dv)));
    }

    /** Small filled dot (used as the category icon / enabled indicator in the ClickGui). */
    public static void dot(GuiGraphics g, float cx, float cy, float radius, int color) {
        roundedRect(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
    }

    /** Bresenham line drawn from square pixels — good enough for tiny check / cross glyphs. */
    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            g.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    /** Green check / red cross toggle glyph at the given top-left, drawn with {@link #line}. */
    public static void checkGlyph(GuiGraphics g, int x, int y, boolean checked) {
        if (checked) {
            int color = new Color(80, 220, 120).getRGB();
            line(g, x, y + 3, x + 2, y + 5, 1, color);
            line(g, x + 2, y + 5, x + 6, y, 1, color);
        } else {
            int color = new Color(225, 80, 90).getRGB();
            line(g, x, y, x + 5, y + 5, 1, color);
            line(g, x + 5, y, x, y + 5, 1, color);
        }
    }

    public static void horizontalGradient(GuiGraphics context, float x1, float y1, float x2, float y2, Color left, Color right) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);

        gradient(context, ix1, iy1, ix2, iy2, left.hashCode(), left.hashCode(), right.hashCode(), right.hashCode());
    }

    public static void verticalGradient(GuiGraphics context, float x1, float y1, float x2, float y2, Color top, Color bottom) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);

        gradient(context, ix1, iy1, ix2, iy2, top.hashCode(), bottom.hashCode(), bottom.hashCode(), top.hashCode());
    }

    public static void gradient(GuiGraphics graphics,
                                int x1, int y1, int x2, int y2,
                                int topLeft, int bottomLeft, int bottomRight, int topRight) {
        graphics.guiRenderState.submitGuiElement(new RectRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()),
                x1, y1, x2, y2,
                topLeft, bottomLeft, bottomRight, topRight,
                graphics.scissorStack.peek()
        ));
    }

    public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color) {
        rectFilled(stack, x1, y1, x2, y2, color);
    }

    public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color, float width) {
        drawHorizontalLine(stack, x1, x2, y1, color, width);
        drawVerticalLine(stack, x2, y1, y2, color, width);
        drawHorizontalLine(stack, x1, x2, y2, color, width);
        drawVerticalLine(stack, x1, y1, y2, color, width);
    }

    protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }

        rectFilled(matrices, x1, y, x2 + 1, y + 1, color);
    }

    protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }

        rectFilled(matrices, x, y1 + 1, x + 1, y2, color);
    }

    protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color, float width) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }

        rectFilled(matrices, x1, y, x2 + width, y + width, color);
    }

    protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color, float width) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }

        rectFilled(matrices, x, y1 + width, x + width, y2, color);
    }

    public static void rectFilled(PoseStack matrix, float x1, float y1, float x2, float y2, int color) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float f = (float) (color >> 24 & 255) / 255.0F;
        float g = (float) (color >> 16 & 255) / 255.0F;
        float h = (float) (color >> 8 & 255) / 255.0F;
        float j = (float) (color & 255) / 255.0F;

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F).setColor(g, h, j, f);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F).setColor(g, h, j, f);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F).setColor(g, h, j, f);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F).setColor(g, h, j, f);

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void horizontalGradient(PoseStack matrix, float x1, float y1, float x2, float y2, Color left, Color right) {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F).setColor(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F).setColor(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F).setColor(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F).setColor(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void verticalGradient(PoseStack matrix, float x1, float y1, float x2, float y2, Color top, Color bottom) {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F).setColor(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F).setColor(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F).setColor(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
        bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F).setColor(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    // 3d
    public static void drawBoxFilled(PoseStack stack, AABB box, Color c) {
        float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.position().x());
        float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.position().y());
        float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.position().z());
        float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.position().x());
        float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.position().y());
        float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.position().z());

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(stack.last().pose(), minX, minY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, minY, maxZ).setColor(c.getRGB());

        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, minZ).setColor(c.getRGB());

        bufferBuilder.addVertex(stack.last().pose(), minX, minY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, minZ).setColor(c.getRGB());

        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, maxZ).setColor(c.getRGB());

        bufferBuilder.addVertex(stack.last().pose(), minX, minY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, minY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), maxX, maxY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, maxZ).setColor(c.getRGB());

        bufferBuilder.addVertex(stack.last().pose(), minX, minY, minZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, minY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, maxZ).setColor(c.getRGB());
        bufferBuilder.addVertex(stack.last().pose(), minX, maxY, minZ).setColor(c.getRGB());

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void drawBoxFilled(PoseStack stack, Vec3 vec, Color c) {
        drawBoxFilled(stack, AABB.unitCubeFromLowerCorner(vec), c);
    }

    public static void drawBoxFilled(PoseStack stack, BlockPos bp, Color c) {
        drawBoxFilled(stack, new AABB(bp), c);
    }

    public static void drawBox(PoseStack stack, AABB box, Color c, float lineWidth) {
        drawBox(stack, Shapes.create(box), c, lineWidth);
    }

    public static void drawBox(PoseStack stack, VoxelShape shape, Color c, float lineWidth) {
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();
        int color = c.getRGB();

        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            addLine(bufferBuilder, pose, color, lineWidth,
                    x1 - camera.x, y1 - camera.y, z1 - camera.z,
                    x2 - camera.x, y2 - camera.y, z2 - camera.z
            );
        });

        Layers.lines().draw(bufferBuilder.buildOrThrow());
    }

    private static void addLine(BufferBuilder buf, PoseStack.Pose pose, int color, float lineWidth,
                                double x1, double y1, double z1, double x2, double y2, double z2) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        buf.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        buf.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }

    public static void drawBox(PoseStack stack, Vec3 vec, Color c, float lineWidth) {
        drawBox(stack, AABB.unitCubeFromLowerCorner(vec), c, lineWidth);
    }

    public static void drawBox(PoseStack stack, BlockPos bp, Color c, float lineWidth) {
        drawBox(stack, new AABB(bp), c, lineWidth);
    }

    public static PoseStack matrixFrom(Vec3 pos) {
        PoseStack matrices = new PoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        matrices.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
        matrices.translate(pos.x() - camera.position().x, pos.y() - camera.position().y, pos.z() - camera.position().z);
        return matrices;
    }
}
