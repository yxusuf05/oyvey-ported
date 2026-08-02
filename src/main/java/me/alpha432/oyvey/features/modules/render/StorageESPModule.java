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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Highlights nearby storage blocks (chests, shulkers, barrels, ender chests, furnaces, hoppers,
 * dispensers/droppers) through walls. Blocks are rescanned on a 250 ms timer, not every frame.
 */
public class StorageESPModule extends Module {
    public final Setting<Integer> radius = num("Radius", 12, 2, 32);
    public final Setting<Boolean> box = bool("Outline", true);
    public final Setting<Boolean> fill = bool("Fill", true);
    public final Setting<Integer> fillAlpha = num("FillAlpha", 40, 0, 150);
    public final Setting<Float> lineWidth = num("LineWidth", 1.2f, 0.1f, 4.0f);
    public final Setting<Color> chestColor = color("ChestColor", 235, 175, 55, 255);
    public final Setting<Color> shulkerColor = color("ShulkerColor", 200, 90, 235, 255);
    public final Setting<Color> otherColor = color("OtherColor", 150, 150, 160, 255);

    private final Timer scanTimer = new Timer();
    private List<BlockPos> chests = new ArrayList<>();
    private List<BlockPos> shulkers = new ArrayList<>();
    private List<BlockPos> others = new ArrayList<>();

    public StorageESPModule() {
        super("StorageESP", "Highlights nearby storage blocks", Category.RENDER);
    }

    @Override
    public void onEnable() {
        scanTimer.reset();
        chests = new ArrayList<>();
        shulkers = new ArrayList<>();
        others = new ArrayList<>();
    }

    @Override
    public void onTick() {
        if (nullCheck() || !scanTimer.passedMs(250)) return;
        scanTimer.reset();

        int r = radius.getValue();
        BlockPos origin = mc.player.blockPosition();
        List<BlockPos> c = new ArrayList<>(), s = new ArrayList<>(), o = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block instanceof ShulkerBoxBlock) s.add(pos.immutable());
            else if (block instanceof ChestBlock || block == Blocks.BARREL || block == Blocks.ENDER_CHEST) c.add(pos.immutable());
            else if (isOther(block)) o.add(pos.immutable());
        }
        chests = c;
        shulkers = s;
        others = o;
    }

    private boolean isOther(Block block) {
        return block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
                || block == Blocks.HOPPER || block == Blocks.DISPENSER || block == Blocks.DROPPER;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        draw(event, chests, chestColor.getValue());
        draw(event, shulkers, shulkerColor.getValue());
        draw(event, others, otherColor.getValue());
    }

    private void draw(Render3DEvent event, List<BlockPos> positions, Color color) {
        for (BlockPos pos : positions) {
            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), pos, ColorUtil.withAlpha(color, fillAlpha.getValue()));
            }
            if (box.getValue()) {
                RenderUtil.drawBox(event.getMatrix(), pos, color, lineWidth.getValue());
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(chests.size() + shulkers.size() + others.size());
    }
}
