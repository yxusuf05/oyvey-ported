package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.mixin.render.gui.MixinAbstractContainerScreen;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps a Totem of Undying in your off-hand.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Packet</b> — instant silent swap into the off-hand via inventory-click packets. Fastest,
 *       no visuals; this is what you want the moment you place an anchor and need the totem back.</li>
 *   <li><b>Legit</b> — actually opens your inventory and (optionally) glides the real OS cursor onto
 *       the totem before clicking it, so an observer sees a human-looking movement instead of an
 *       instant teleport.</li>
 * </ul>
 *
 * <p>{@code KeepInHotbar} additionally parks a spare totem in a configurable hotbar slot, so a
 * quick number-key press always has one ready without touching the off-hand routine.
 */
public class AutoTotemModule extends Module {
    private static final int OFFHAND_SLOT = 45;

    public enum Mode {PACKET, LEGIT}

    public final Setting<Mode> mode = mode("Mode", Mode.PACKET);
    public final Setting<Integer> delay = num("Delay", 50, 0, 500);

    public final Setting<Boolean> openInventory = bool("OpenInventory", true);
    public final Setting<Boolean> moveMouse = bool("MoveMouse", true);
    public final Setting<Integer> mouseSpeed = num("MouseSpeed", 6, 1, 20);

    public final Setting<Boolean> keepInHotbar = bool("KeepInHotbar", false);
    public final Setting<Integer> hotbarSlot = num("HotbarSlot", 9, 1, 9);

    private final Timer timer = new Timer();

    // Legit-mode state.
    private boolean opened;
    private double cursorX, cursorY;

    public AutoTotemModule() {
        super("AutoTotem", "Keeps a totem in your off-hand", Category.COMBAT);
        openInventory.setVisibility(v -> mode.getValue() == Mode.LEGIT);
        moveMouse.setVisibility(v -> mode.getValue() == Mode.LEGIT);
        mouseSpeed.setVisibility(v -> mode.getValue() == Mode.LEGIT);
        hotbarSlot.setVisibility(v -> keepInHotbar.getValue());
    }

    @Override
    public void onDisable() {
        closeLegit();
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.gameMode == null) return;

        if (keepInHotbar.getValue()) ensureHotbarTotem();

        boolean needsTotem = !offhandTotem() && findTotem() != -1;
        if (!needsTotem) {
            closeLegit();
            return;
        }
        if (!timer.passedMs(delay.getValue())) return;

        if (mode.getValue() == Mode.LEGIT) {
            legitRefill();
        } else {
            packetRefill();
            timer.reset();
        }
    }

    // ------------------------------------------------------------------ packet mode

    private void packetRefill() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;
        InventoryMenu menu = mc.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return;

        int totem = findTotem();
        if (totem == -1) return;

        int id = menu.containerId;
        mc.gameMode.handleInventoryMouseClick(id, totem, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(id, OFFHAND_SLOT, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(id, totem, 0, ClickType.PICKUP, mc.player);
    }

    // ------------------------------------------------------------------ legit mode

    private void legitRefill() {
        if (openInventory.getValue() && !(mc.screen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            opened = true;
            return; // let the screen initialise (leftPos/topPos) before we read it
        }

        int totem = findTotem();
        if (totem == -1) {
            closeLegit();
            return;
        }

        if (moveMouse.getValue() && mc.screen instanceof AbstractContainerScreen<?> screen) {
            Slot slot = slotForContainerIndex(totem);
            if (slot != null && !glideCursorTo(screen, slot)) {
                return; // still travelling toward the slot
            }
        }

        packetRefill();
        timer.reset();
        closeLegit();
    }

    /**
     * Moves the OS cursor a step toward the slot centre. Returns true once it is basically on top of
     * the slot. Real pixels = GUI pixels * gui scale, matching how MouseHandler reports positions.
     */
    private boolean glideCursorTo(AbstractContainerScreen<?> screen, Slot slot) {
        int scale = mc.getWindow().getGuiScale();
        MixinAbstractContainerScreen accessor = (MixinAbstractContainerScreen) screen;
        double targetGuiX = accessor.oyvey$getLeftPos() + slot.x + 8.0;
        double targetGuiY = accessor.oyvey$getTopPos() + slot.y + 8.0;
        double targetX = targetGuiX * scale;
        double targetY = targetGuiY * scale;

        if (cursorX == 0 && cursorY == 0) {
            cursorX = mc.mouseHandler.xpos();
            cursorY = mc.mouseHandler.ypos();
        }

        double factor = mouseSpeed.getValue() / 20.0;
        cursorX += (targetX - cursorX) * factor;
        cursorY += (targetY - cursorY) * factor;
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), cursorX, cursorY);

        return Math.hypot(targetX - cursorX, targetY - cursorY) <= 2.0 * scale;
    }

    private void closeLegit() {
        if (opened && mc.screen instanceof InventoryScreen) {
            mc.setScreen(null);
        }
        opened = false;
        cursorX = 0;
        cursorY = 0;
    }

    // ------------------------------------------------------------------ helpers

    private void ensureHotbarTotem() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;
        int target = hotbarSlot.getValue() - 1; // 1-9 -> 0-8
        if (mc.player.getInventory().getItem(target).is(Items.TOTEM_OF_UNDYING)) return;

        InventoryMenu menu = mc.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return;

        // Container slot ids: hotbar 0-8 -> 36-44, so target hotbar slot is 36 + target.
        int source = -1;
        for (int i = 9; i <= 44; i++) {
            if (i == 36 + target) continue;
            if (menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) {
                source = i;
                break;
            }
        }
        if (source == -1) return;

        int id = menu.containerId;
        mc.gameMode.handleInventoryMouseClick(id, source, target, ClickType.SWAP, mc.player);
    }

    private boolean offhandTotem() {
        return mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    /** Container slot id of a totem anywhere in the main inventory / hotbar, or -1. */
    private int findTotem() {
        InventoryMenu menu = mc.player.inventoryMenu;
        for (int i = 9; i <= 44; i++) {
            if (menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private Slot slotForContainerIndex(int index) {
        InventoryMenu menu = mc.player.inventoryMenu;
        return index >= 0 && index < menu.slots.size() ? menu.slots.get(index) : null;
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue() == Mode.LEGIT ? "Legit" : "Packet";
    }
}
