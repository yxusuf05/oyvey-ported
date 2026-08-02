package me.alpha432.oyvey.features.gui.items.buttons;


import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.gui.OyVeyGui;
import me.alpha432.oyvey.features.modules.client.ClickGuiModule;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;

public class BooleanButton
        extends Button {
    private final Setting<Boolean> setting;

    public BooleanButton(Setting<Boolean> setting) {
        super(setting.getName());
        this.setting = setting;
        this.width = 15;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        if (this.isHovering(mouseX, mouseY)) {
            float rad = ClickGuiModule.getInstance().rounding.getValue() * 0.4f;
            RenderUtil.roundedRect(context, this.x - 2, this.y, this.x + (float) this.width + 8f, this.y + (float) this.height, rad, new Color(255, 255, 255, 26).getRGB());
        }
        RenderUtil.checkGlyph(context, (int) this.x, (int) (this.y + this.height / 2f - 3f), this.getState());
        drawString(this.getName(), this.x + 10.0f, this.y + this.height / 2f - 4f,
                this.getState() ? new Color(0xE0, 0xE0, 0xE8).getRGB() : new Color(0x8E, 0x8E, 0x9A).getRGB());
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isHovering(mouseX, mouseY)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public void toggle() {
        this.setting.setValue(!this.setting.getValue());
    }

    @Override
    public boolean getState() {
        return this.setting.getValue();
    }
}