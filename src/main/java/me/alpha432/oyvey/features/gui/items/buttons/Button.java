package me.alpha432.oyvey.features.gui.items.buttons;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.gui.OyVeyGui;
import me.alpha432.oyvey.features.gui.Widget;
import me.alpha432.oyvey.features.gui.items.Item;
import me.alpha432.oyvey.features.modules.client.ClickGuiModule;
import me.alpha432.oyvey.util.AnimationUtil;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;

public class Button
        extends Item {
    private boolean state;

    private final AnimationUtil.Animation hoverAnim = new AnimationUtil.Animation(0f, 16f);
    private final AnimationUtil.Animation enableAnim = new AnimationUtil.Animation(0f, 16f);

    public Button(String name) {
        super(name);
        this.height = 15;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = this.isHovering(mouseX, mouseY);
        hoverAnim.setTarget(hovering ? 1f : 0f);
        enableAnim.setTarget(this.getState() ? 1f : 0f);
        float h = hoverAnim.update();
        float e = enableAnim.update();

        float rad = ClickGuiModule.getInstance().rounding.getValue() * 0.5f;
        float bx1 = this.x + 1f;
        float by1 = this.y;
        float bx2 = this.x + (float) this.width - 1f;
        float by2 = this.y + (float) this.height - 1.5f;

        // Subtle hover row, no heavy fills — the state is carried by the text colour instead.
        int hoverAlpha = (int) (h * 42f);
        if (hoverAlpha > 1) {
            RenderUtil.roundedRect(context, bx1, by1, bx2, by2, rad, new Color(255, 255, 255, hoverAlpha).getRGB());
        }

        Color accent = new Color(OyVey.colorManager.getColorWithAlpha(this.y, 255), true);
        Color idle = ColorUtil.interpolate(new Color(0x9A, 0x9A, 0xA6), Color.WHITE, h * 0.7f);
        int textColor = ColorUtil.interpolate(idle, accent, e).getRGB();
        drawString(this.getName(), this.x + 5.0f, this.y - 2.0f - (float) OyVeyGui.getClickGui().getTextOffset(), textColor);

        // Enabled indicator dot on the right edge.
        if (e > 0.05f) {
            RenderUtil.dot(context, bx2 - 3.5f, this.y + this.height / 2f - 1f, 1.6f * e, accent.getRGB());
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.onMouseClick();
        }
    }

    public void onMouseClick() {
        this.state = !this.state;
        this.toggle();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    public void toggle() {
    }

    public boolean getState() {
        return this.state;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    public boolean isHovering(int mouseX, int mouseY) {
        for (Widget widget : OyVeyGui.getClickGui().getComponents()) {
            if (!widget.drag) continue;
            return false;
        }
        return (float) mouseX >= this.getX() && (float) mouseX <= this.getX() + (float) this.getWidth() && (float) mouseY >= this.getY() && (float) mouseY < this.getY() + (float) this.height;
    }
}