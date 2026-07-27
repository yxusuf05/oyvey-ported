package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;

/**
 * Keeps a Totem of Undying in your off-hand, automatically moving one from the inventory whenever
 * the slot is empty (for example right after a totem pops). Only acts while the normal survival
 * inventory is the open container and the cursor is empty, so it never fights chest interactions.
 */
public class AutoTotemModule extends Module {
    private static final int OFFHAND_SLOT = 45;

    public final Setting<Integer> delay = num("Delay", 50, 0, 500);

    private final Timer timer = new Timer();

    public AutoTotemModule() {
        super("AutoTotem", "Keeps a totem in your off-hand", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.gameMode == null) return;
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;

        InventoryMenu menu = mc.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return;
        if (menu.getSlot(OFFHAND_SLOT).getItem().is(Items.TOTEM_OF_UNDYING)) return;

        int totemSlot = -1;
        for (int i = 9; i <= 44; i++) {
            if (menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) {
                totemSlot = i;
                break;
            }
        }
        if (totemSlot == -1 || !timer.passedMs(delay.getValue())) return;

        int id = menu.containerId;
        mc.gameMode.handleInventoryMouseClick(id, totemSlot, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(id, OFFHAND_SLOT, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(id, totemSlot, 0, ClickType.PICKUP, mc.player);
        timer.reset();
    }
}
