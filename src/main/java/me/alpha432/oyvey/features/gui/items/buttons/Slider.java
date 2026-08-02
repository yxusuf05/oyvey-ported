package me.alpha432.oyvey.features.gui.items.buttons;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.gui.OyVeyGui;
import me.alpha432.oyvey.features.gui.Widget;
import me.alpha432.oyvey.features.modules.client.ClickGuiModule;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class Slider
        extends Button {
    private final Number min;
    private final Number max;
    private final int difference;
    public Setting<Number> setting;

    public Slider(Setting<Number> setting) {
        super(setting.getName());
        this.setting = setting;
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.difference = this.max.intValue() - this.min.intValue();
        this.width = 15;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        this.dragSetting(mouseX, mouseY);
        int accent = OyVey.colorManager.getColorWithAlpha(y, 255);
        float left = this.x + 2.5f;
        float right = this.x + (float) this.width + 7.4f - 2.5f;
        float trackY = this.y + (float) this.height - 4.5f;

        RenderUtil.rect(context, left, trackY, right, trackY + 1.6f, new Color(255, 255, 255, 38).getRGB());
        float pct = Mth.clamp(this.partialMultiplier(), 0f, 1f);
        if (pct > 0f) {
            RenderUtil.rect(context, left, trackY, left + (right - left) * pct, trackY + 1.6f, accent);
        }

        drawString(this.getName(), this.x + 3.0f, this.y - 2.6f - (float) OyVeyGui.getClickGui().getTextOffset(), new Color(0xC8, 0xC8, 0xD2).getRGB());
        String value = this.setting.getValue() instanceof Integer
                ? String.valueOf(this.setting.getValue())
                : String.valueOf(MathUtil.round(this.setting.getValue().floatValue(), 1));
        drawString(value, right - mc.font.width(value), this.y - 2.6f - (float) OyVeyGui.getClickGui().getTextOffset(), new Color(0x9A, 0x9A, 0xA6).getRGB());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.isHovering(mouseX, mouseY)) {
            this.setSettingFromX(mouseX);
        }
    }

    @Override
    public boolean isHovering(int mouseX, int mouseY) {
        for (Widget widget : OyVeyGui.getClickGui().getComponents()) {
            if (!widget.drag) continue;
            return false;
        }
        return (float) mouseX >= this.getX() && (float) mouseX <= this.getX() + (float) this.getWidth() + 8.0f && (float) mouseY >= this.getY() && (float) mouseY < this.getY() + (float) this.height;
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    private void dragSetting(int mouseX, int mouseY) {
        if (this.isHovering(mouseX, mouseY) && GLFW.glfwGetMouseButton(mc.getWindow().handle(), 0) == 1) {
            this.setSettingFromX(mouseX);
        }
    }

    private void setSettingFromX(int mouseX) {
        float percent = ((float) mouseX - this.x) / ((float) this.width + 7.4f);
        if (this.setting.getValue() instanceof Double) {
            double result = (Double) this.setting.getMin() + (double) ((float) this.difference * percent);
            this.setting.setValue((double) Math.round(10.0 * result) / 10.0);
        } else if (this.setting.getValue() instanceof Float) {
            float result = this.setting.getMin().floatValue() + (float) this.difference * percent;
            this.setting.setValue((float) Math.round(10.0f * result) / 10.0f);
        } else if (this.setting.getValue() instanceof Integer) {
            this.setting.setValue((Integer) this.setting.getMin() + (int) ((float) this.difference * percent));
        }
    }

    private float middle() {
        return this.max.floatValue() - this.min.floatValue();
    }

    private float part() {
        return (this.setting.getValue()).floatValue() - this.min.floatValue();
    }

    private float partialMultiplier() {
        return this.part() / this.middle();
    }
}