package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.InteractionUtil;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.inventory.Result;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static me.alpha432.oyvey.util.inventory.InventoryUtil.HOTBAR_SCOPE;

/**
 * Walls your feet in with obsidian so nobody can place a crystal right next to you. Runs until the
 * ring is complete and then idles; re-fills automatically the moment a block gets blown out.
 */
public class SurroundModule extends Module {
    public final Setting<Integer> delay = num("Delay", 50, 0, 500);
    public final Setting<Integer> blocksPerTick = num("BlocksPerTick", 4, 1, 8);
    public final Setting<Boolean> corners = bool("Corners", false);
    public final Setting<Boolean> floor = bool("Floor", true);
    public final Setting<Boolean> disableOnComplete = bool("DisableOnComplete", false);
    public final Setting<Boolean> inventory = bool("UseInventory", false);

    private final Timer timer = new Timer();

    public SurroundModule() {
        super("Surround", "Surrounds your feet with obsidian", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.gameMode == null) return;
        if (!timer.passedMs(delay.getValue())) return;

        List<BlockPos> targets = missingPositions();
        if (targets.isEmpty()) {
            if (disableOnComplete.getValue()) disable();
            return;
        }

        Result obsidian = InventoryUtil.find(Items.OBSIDIAN,
                inventory.getValue() ? InventoryUtil.FULL_SCOPE : HOTBAR_SCOPE);
        if (!obsidian.found()) return;

        int budget = blocksPerTick.getValue();
        int lastSlot = InventoryUtil.selected();
        if (!InventoryUtil.swap(obsidian)) return;

        for (BlockPos pos : targets) {
            if (budget-- <= 0) break;
            InteractionUtil.place(pos, false, obsidian.hand());
        }

        InventoryUtil.swapBack(obsidian, lastSlot);
        timer.reset();
    }

    /** Positions of the ring that are still open, closest first so gaps get plugged fastest. */
    private List<BlockPos> missingPositions() {
        BlockPos feet = mc.player.blockPosition();
        List<BlockPos> positions = new ArrayList<>();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            addIfOpen(positions, feet.relative(direction));
        }
        if (corners.getValue()) {
            addIfOpen(positions, feet.north().east());
            addIfOpen(positions, feet.north().west());
            addIfOpen(positions, feet.south().east());
            addIfOpen(positions, feet.south().west());
        }
        if (floor.getValue()) {
            addIfOpen(positions, feet.below());
        }
        return positions;
    }

    private void addIfOpen(List<BlockPos> positions, BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return;
        if (!InteractionUtil.isPlaceable(pos, true)) return;
        positions.add(pos);
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(missingPositions().size());
    }
}
