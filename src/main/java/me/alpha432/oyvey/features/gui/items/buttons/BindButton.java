package me.alpha432.oyvey.features.gui.items.buttons;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.gui.OyVeyGui;
import me.alpha432.oyvey.features.modules.client.ClickGuiModule;
import me.alpha432.oyvey.features.settings.Bind;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.KeyboardUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class BindButton
        extends Button {
    private final Setting<Bind> setting;
    public boolean isListening;

    public BindButton(Setting<Bind> setting) {
        super(setting.getName());
        this.setting = setting;
        this.width = 15;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        if (this.isHovering(mouseX, mouseY) || this.isListening) {
            float rad = ClickGuiModule.getInstance().rounding.getValue() * 0.4f;
            RenderUtil.roundedRect(context, this.x - 2, this.y, this.x + (float) this.width + 8f, this.y + (float) this.height, rad, new Color(255, 255, 255, 26).getRGB());
        }
        float right = this.x + (float) this.width + 8f;
        float textY = this.y + this.height / 2f - 4f;
        drawString("Bind:", this.x, textY, new Color(0xC8, 0xC8, 0xD2).getRGB());
        String str = this.isListening ? "..." : KeyboardUtil.getKeyName(setting.getValue());
        drawString(str, right - mc.font.width(str), textY,
                this.isListening ? OyVey.colorManager.getColorWithAlpha(y, 255) : new Color(0x9A, 0x9A, 0xA6).getRGB());
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isListening) {
            if (mouseButton != 0 && mouseButton != 1) {
                this.setting.setValue(new Bind(-mouseButton - 2));
                this.onMouseClick();
            }
        } else if (this.isHovering(mouseX, mouseY)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public void onKeyPressed(int key) {
        if (this.isListening) {
            Bind bind = new Bind(key);
            if (key == GLFW.GLFW_KEY_DELETE
                    || key == GLFW.GLFW_KEY_BACKSPACE
                    || key == GLFW.GLFW_KEY_ESCAPE) {
                bind = new Bind(-1);
            }
            this.setting.setValue(bind);
            this.onMouseClick();
        }
    }

    @Override
    public void toggle() {
        this.isListening = !this.isListening;
    }

    @Override
    public boolean getState() {
        return !this.isListening;
    }
}