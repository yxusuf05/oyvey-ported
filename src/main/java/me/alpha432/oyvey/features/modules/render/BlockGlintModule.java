package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Highlights ("glints") nearby blocks that matter in crystal PvP - obsidian, crying obsidian,
 * respawn anchors and ender chests - with a filled box and an outline. The surrounding blocks
 * are scanned on a throttled timer rather than every frame so it stays cheap.
 */
public class BlockGlintModule extends Module {
    public final Setting<Integer> radius = num("Radius", 6, 1, 12);
    public final Setting<Boolean> box = bool("Outline", true);
    public final Setting<Boolean> fill = bool("Fill", true);
    public final Setting<Integer> fillAlpha = num("FillAlpha", 45, 0, 255);
    public final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 3.0f);
    public final Setting<Color> color = color("Color", 170, 0, 255, 255);

    public final Setting<Boolean> obsidian = bool("Obsidian", true);
    public final Setting<Boolean> cryingObsidian = bool("CryingObsidian", true);
    public final Setting<Boolean> respawnAnchor = bool("RespawnAnchor", true);
    public final Setting<Boolean> enderChest = bool("EnderChest", true);

    private final Timer scanTimer = new Timer();
    private List<BlockPos> positions = new ArrayList<>();

    public BlockGlintModule() {
        super("BlockGlint", "Highlights important blocks around you", Category.RENDER);
        fillAlpha.setVisibility(v -> fill.getValue());
        lineWidth.setVisibility(v -> box.getValue());
    }

    @Override
    public void onEnable() {
        scanTimer.reset();
        positions = new ArrayList<>();
    }

    private boolean matches(Block block) {
        if (obsidian.getValue() && block == Blocks.OBSIDIAN) return true;
        if (cryingObsidian.getValue() && block == Blocks.CRYING_OBSIDIAN) return true;
        if (respawnAnchor.getValue() && block == Blocks.RESPAWN_ANCHOR) return true;
        return enderChest.getValue() && block == Blocks.ENDER_CHEST;
    }

    @Override
    public void onTick() {
        if (nullCheck() || !scanTimer.passedMs(200)) return;
        scanTimer.reset();

        int r = radius.getValue();
        BlockPos origin = mc.player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (matches(mc.level.getBlockState(pos).getBlock())) {
                found.add(pos.immutable());
            }
        }
        positions = found;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || positions.isEmpty()) return;

        Color outline = ColorUtil.withAlpha(color.getValue(), 255);
        Color filled = ColorUtil.withAlpha(color.getValue(), fillAlpha.getValue());

        for (BlockPos pos : positions) {
            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), pos, filled);
            }
            if (box.getValue()) {
                RenderUtil.drawBox(event.getMatrix(), pos, outline, lineWidth.getValue());
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(positions.size());
    }
}
