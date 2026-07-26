package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHudModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final Setting<Boolean> vertical = bool("Vertical", false);
    public final Setting<Boolean> durability = bool("Durability", true);

    public ArmorHudModule() {
        super("ArmorHud", "Displays your armor and its durability", 74, 16);
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);
        if (nullCheck()) return;

        GuiGraphics context = e.getContext();
        float x = getX();
        float y = getY();
        int step = 18;
        int index = 0;

        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            int itemX = (int) (vertical.getValue() ? x : x + index * step);
            int itemY = (int) (vertical.getValue() ? y + index * step : y);

            context.renderItem(stack, itemX, itemY);
            if (durability.getValue()) {
                context.renderItemDecorations(mc.font, stack, itemX, itemY);
            }
            index++;
        }

        if (vertical.getValue()) {
            setWidth(16);
            setHeight(Math.max(16, index * step));
        } else {
            setWidth(Math.max(16, index * step));
            setHeight(16);
        }
    }
}
