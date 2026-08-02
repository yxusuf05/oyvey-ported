package me.blockoutlines.render;

import me.blockoutlines.BlockOutlines;
import me.blockoutlines.config.BlockOutlinesConfig;
import me.blockoutlines.config.BoxShape;
import me.blockoutlines.config.TargetMode;
import me.blockoutlines.util.Colors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Feeds the outlines into Minecraft's gizmo collector, which happens once per frame while the
 * level renderer extracts its render state. Coordinates are plain world coordinates, the game
 * takes care of the camera offset, fog and the depth handling.
 */
public final class HighlightRenderer {
    private static boolean errorLogged;

    private HighlightRenderer() {
    }

    public static void emit() {
        try {
            emitUnsafe();
        } catch (Throwable throwable) {
            // Never take the game down over a highlight.
            if (!errorLogged) {
                errorLogged = true;
                BlockOutlines.LOGGER.error("[BlockOutlines] Failed to emit outlines", throwable);
            }
        }
    }

    private static void emitUnsafe() {
        BlockOutlinesConfig config = BlockOutlines.config();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!config.enabled || level == null || minecraft.player == null || !camera.isInitialized()) {
            return;
        }

        emitTarget(config, minecraft, level);
        emitSearch(config, level, camera);
    }

    // ------------------------------------------------------------------ targeted block
    private static void emitTarget(BlockOutlinesConfig config, Minecraft minecraft, ClientLevel level) {
        if (config.targetMode != TargetMode.CUSTOM) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        int stroke = config.targetRainbow
                ? Colors.rainbow(config.targetColor, config.rainbowSpeed, 0.0f)
                : config.targetColor;
        int fill = 0;
        if (config.targetFill) {
            fill = config.targetRainbow
                    ? Colors.withAlpha(Colors.rainbow(config.targetFillColor, config.rainbowSpeed, 0.0f),
                            ARGB.alpha(config.targetFillColor))
                    : config.targetFillColor;
        }
        GizmoStyle style = GizmoStyle.strokeAndFill(stroke, config.targetLineWidth, fill);

        for (AABB box : boxesOf(config.targetShape, state, level, pos, config.targetExpand)) {
            GizmoProperties properties = Gizmos.cuboid(box, style, false);
            if (config.targetThroughWalls) {
                properties.setAlwaysOnTop();
            }
        }
    }

    // -------------------------------------------------------------------- block search
    private static void emitSearch(BlockOutlinesConfig config, ClientLevel level, Camera camera) {
        if (!config.searchEnabled || config.blocks.isEmpty()) {
            return;
        }

        Vec3 eye = camera.position();
        List<BlockScanner.Hit> hits = BlockOutlines.scanner().hits(level, eye);
        if (hits.isEmpty()) {
            return;
        }

        Vec3 tracerStart = tracerStart(camera);
        float expand = config.searchExpand;
        int index = 0;

        for (BlockScanner.Hit hit : hits) {
            int base = config.searchRainbow
                    ? Colors.rainbow(hit.color(), config.rainbowSpeed, (index++ % 32) / 64.0f)
                    : hit.color();

            float fade = 1.0f;
            if (config.distanceFade) {
                double dx = hit.centerX() - eye.x;
                double dy = hit.centerY() - eye.y;
                double dz = hit.centerZ() - eye.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                fade = (float) Math.max(0.15, 1.0 - distance / config.searchRange);
            }

            int stroke = config.searchMode.outline() ? Colors.fade(base, fade) : 0;
            int fill = config.searchMode.fill()
                    ? Colors.fade(Colors.withAlpha(base, config.searchFillAlpha), fade)
                    : 0;
            GizmoStyle style = GizmoStyle.strokeAndFill(stroke, config.searchLineWidth, fill);

            AABB box = new AABB(hit.minX(), hit.minY(), hit.minZ(),
                    hit.maxX() + 1.0, hit.maxY() + 1.0, hit.maxZ() + 1.0).inflate(expand);
            if (config.searchShape == BoxShape.BLOCK_SHAPE && isSingleBlock(hit)) {
                BlockPos pos = new BlockPos(hit.minX(), hit.minY(), hit.minZ());
                for (AABB part : boxesOf(BoxShape.BLOCK_SHAPE, level.getBlockState(pos), level, pos, expand)) {
                    GizmoProperties properties = Gizmos.cuboid(part, style, false);
                    if (config.searchThroughWalls) {
                        properties.setAlwaysOnTop();
                    }
                }
            } else {
                GizmoProperties properties = Gizmos.cuboid(box, style, false);
                if (config.searchThroughWalls) {
                    properties.setAlwaysOnTop();
                }
            }

            if (config.searchTracers) {
                int tracerColor = config.searchRainbow ? Colors.withAlpha(base, ARGB.alpha(config.searchTracerColor))
                        : config.searchTracerColor;
                GizmoProperties tracer = Gizmos.line(tracerStart,
                        new Vec3(hit.centerX(), hit.centerY(), hit.centerZ()),
                        Colors.fade(tracerColor, fade), config.searchTracerWidth);
                if (config.searchThroughWalls) {
                    tracer.setAlwaysOnTop();
                }
            }
        }
    }

    // -------------------------------------------------------------------------- shared
    private static boolean isSingleBlock(BlockScanner.Hit hit) {
        return hit.minX() == hit.maxX() && hit.minY() == hit.maxY() && hit.minZ() == hit.maxZ();
    }

    /** The boxes a single block is drawn with, already moved to its world position. */
    private static List<AABB> boxesOf(BoxShape shape, BlockState state, ClientLevel level, BlockPos pos, float expand) {
        if (shape == BoxShape.FULL_CUBE) {
            return List.of(new AABB(pos).inflate(expand));
        }

        VoxelShape voxelShape = state.getShape(level, pos);
        if (voxelShape.isEmpty()) {
            return List.of(new AABB(pos).inflate(expand));
        }
        return voxelShape.toAabbs().stream()
                .map(box -> box.move(pos.getX(), pos.getY(), pos.getZ()).inflate(expand))
                .toList();
    }

    /** Slightly in front of the camera, so tracers appear to start at the crosshair. */
    private static Vec3 tracerStart(Camera camera) {
        Vector3fc forward = camera.forwardVector();
        return camera.position().add(forward.x(), forward.y(), forward.z());
    }
}
