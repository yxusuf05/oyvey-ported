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
    /** Height of the module card itself; the gap between cards is added by the parent widget. */
    public static final int CARD_HEIGHT = 17;

    private boolean state;

    private final AnimationUtil.Animation hoverAnim = new AnimationUtil.Animation(0f, 16f);
    private final AnimationUtil.Animation enableAnim = new AnimationUtil.Animation(0f, 16f);

    public Button(String name) {
        super(name);
        this.height = CARD_HEIGHT;
    }

    @Override
    public void drawScreen(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        boolean hovering = this.isHovering(mouseX, mouseY);
        hoverAnim.setTarget(hovering ? 1f : 0f);
        enableAnim.setTarget(this.getState() ? 1f : 0f);
        float h = hoverAnim.update();
        float e = enableAnim.update();

        float rad = ClickGuiModule.getInstance().rounding.getValue() * 0.7f;
        float bx1 = this.x;
        float by1 = this.y;
        float bx2 = this.x + (float) this.width;
        float by2 = this.y + CARD_HEIGHT;

        // Every module is its own rounded card, lifted slightly on hover.
        int base = 0x2A + (int) (h * 0x18);
        RenderUtil.roundedRect(context, bx1, by1, bx2, by2, rad, new Color(base, base - 6, base + 8, 235).getRGB());

        Color accent = new Color(OyVey.colorManager.getColorWithAlpha(this.y, 255), true);
        Color idle = ColorUtil.interpolate(new Color(0x8E, 0x8E, 0x9A), Color.WHITE, h * 0.6f);
        int textColor = ColorUtil.interpolate(idle, accent, e).getRGB();

        // Reserve room on the right for the enabled dot so long names never collide with it.
        RenderUtil.centeredFittedText(context, this.getName(), bx1, bx2 - 6f, by1 + CARD_HEIGHT / 2f - 4f, textColor);

        // Enabled indicator dot on the right edge, like the reference design.
        if (e > 0.05f) {
            RenderUtil.dot(context, bx2 - 5f, by1 + CARD_HEIGHT / 2f, 1.7f * e, accent.getRGB());
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
        return CARD_HEIGHT;
    }

    public boolean isHovering(int mouseX, int mouseY) {
        for (Widget widget : OyVeyGui.getClickGui().getComponents()) {
            if (!widget.drag) continue;
            return false;
        }
        return (float) mouseX >= this.getX() && (float) mouseX <= this.getX() + (float) this.getWidth() && (float) mouseY >= this.getY() && (float) mouseY < this.getY() + (float) CARD_HEIGHT;
    }
}