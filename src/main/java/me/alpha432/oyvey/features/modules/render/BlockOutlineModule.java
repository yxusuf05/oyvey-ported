package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.impl.render.RenderBlockOutlineEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;

/**
 * Draws a fully customizable outline (and optional fill) around the block you are aiming at and,
 * unlike the vanilla/default block outline, can ignore blocks that have an end crystal sitting on
 * them so it stays clean during crystal PvP.
 */
public class BlockOutlineModule extends Module {
    public final Setting<Color> outlineColor = color("OutlineColor", 130, 0, 255, 255);
    public final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.1f, 5.0f);
    public final Setting<Boolean> fill = bool("Fill", true);
    public final Setting<Color> fillColor = color("FillColor", 130, 0, 255, 55);
    public final Setting<Boolean> chroma = bool("Chroma", false);
    public final Setting<Boolean> replaceVanilla = bool("ReplaceVanilla", true);
    public final Setting<Boolean> ignoreCrystals = bool("IgnoreCrystals", true);

    public BlockOutlineModule() {
        super("BlockOutline", "Custom colored outline of the block you are aiming at", Category.RENDER);
        fillColor.setVisibility(v -> fill.getValue());
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (!(mc.hitResult instanceof BlockHitResult result) || result.getType() == HitResult.Type.MISS) return;

        BlockPos pos = result.getBlockPos();
        if (ignoreCrystals.getValue() && hasCrystal(pos)) return;

        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) return;
        AABB box = shape.bounds().move(pos);

        Color outline = chroma.getValue()
                ? ColorUtil.withAlpha(ColorUtil.rainbow(0), outlineColor.getValue().getAlpha())
                : outlineColor.getValue();

        if (fill.getValue()) {
            Color filled = chroma.getValue()
                    ? ColorUtil.withAlpha(ColorUtil.rainbow(0), fillColor.getValue().getAlpha())
                    : fillColor.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), box, filled);
        }
        RenderUtil.drawBox(event.getMatrix(), box, outline, lineWidth.getValue());
    }

    private boolean hasCrystal(BlockPos pos) {
        AABB region = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
        return !mc.level.getEntitiesOfClass(EndCrystal.class, region).isEmpty();
    }

    @Subscribe
    public void onRenderBlockOutline(RenderBlockOutlineEvent event) {
        if (replaceVanilla.getValue()) event.cancel();
    }
}
