package me.blockoutlines.gui;

import me.blockoutlines.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.util.function.IntConsumer;

/** Red / green / blue / alpha sliders plus a hex field, applied live while dragging. */
public class ColorPickerScreen extends Screen {
    private static final int[] PRESETS = {
            0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00E5FF,
            0xFF007AFF, 0xFFAF52DE, 0xFFFF2D95, 0xFFFFFFFF, 0xFF000000
    };

    private final @Nullable Screen parent;
    private final String label;
    private final IntConsumer onChange;
    private final int original;
    private int color;

    private @Nullable EditBox hexField;
    private boolean updatingHexField;
    private final NumberSlider[] channels = new NumberSlider[4];
    private boolean draggingChannel;

    public ColorPickerScreen(@Nullable Screen parent, String label, int color, IntConsumer onChange) {
        super(Component.literal(label));
        this.parent = parent;
        this.label = label;
        this.color = color;
        this.original = color;
        this.onChange = onChange;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 76;

        channels[0] = channelSlider("Red", ARGB.red(color), top, value ->
                apply(ARGB.color(ARGB.alpha(color), value, ARGB.green(color), ARGB.blue(color))));
        channels[1] = channelSlider("Green", ARGB.green(color), top + 24, value ->
                apply(ARGB.color(ARGB.alpha(color), ARGB.red(color), value, ARGB.blue(color))));
        channels[2] = channelSlider("Blue", ARGB.blue(color), top + 48, value ->
                apply(ARGB.color(ARGB.alpha(color), ARGB.red(color), ARGB.green(color), value)));
        channels[3] = channelSlider("Opacity", ARGB.alpha(color), top + 72, value ->
                apply(Colors.withAlpha(color, value)));
        for (NumberSlider channel : channels) {
            addRenderableWidget(channel);
        }

        hexField = new EditBox(font, centerX - 100, top + 96, 200, 20, Component.literal("Hex"));
        hexField.setMaxLength(9);
        hexField.setValue(Colors.toHex(color));
        hexField.setResponder(text -> {
            if (!updatingHexField) {
                int parsed = Colors.parseHex(text, color);
                if (parsed != color) {
                    color = parsed;
                    onChange.accept(color);
                }
            }
        });
        addRenderableWidget(hexField);

        int presetWidth = 20;
        int presetsX = centerX - (PRESETS.length * presetWidth) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            int preset = PRESETS[i];
            addRenderableWidget(new PresetButton(presetsX + i * presetWidth, top + 122, presetWidth - 2, 18, preset,
                    () -> apply(Colors.withAlpha(preset, ARGB.alpha(color)))));
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - 102, top + 148, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            apply(original);
            onClose();
        }).bounds(centerX + 2, top + 148, 100, 20).build());
    }

    private NumberSlider channelSlider(String name, int value, int y, java.util.function.IntConsumer setter) {
        NumberSlider slider = new NumberSlider(200, 20, name, "", value, 0, 255, 1, true, newValue -> {
            // The dragged slider already sits at the right spot, moving it again would fight the drag.
            draggingChannel = true;
            setter.accept((int) newValue);
            draggingChannel = false;
        });
        slider.setPosition(width / 2 - 100, y);
        return slider;
    }

    private void apply(int newColor) {
        color = newColor;
        onChange.accept(color);
        if (!draggingChannel && channels[0] != null) {
            channels[0].setNumber(ARGB.red(color));
            channels[1].setNumber(ARGB.green(color));
            channels[2].setNumber(ARGB.blue(color));
            channels[3].setNumber(ARGB.alpha(color));
        }
        if (hexField != null) {
            updatingHexField = true;
            hexField.setValue(Colors.toHex(color));
            updatingHexField = false;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, Component.literal(label), width / 2, height / 2 - 112, 0xFFFFFFFF);

        int previewX = width / 2 - 100;
        int previewY = height / 2 - 96;
        guiGraphics.fill(previewX, previewY, previewX + 200, previewY + 14, 0xFF000000);
        guiGraphics.fill(previewX + 1, previewY + 1, previewX + 199, previewY + 13, 0xFFFFFFFF);
        guiGraphics.fill(previewX + 2, previewY + 2, previewX + 198, previewY + 12, color);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Small solid colour swatch that behaves like a button. */
    private static class PresetButton extends Button {
        private final int color;

        PresetButton(int x, int y, int width, int height, int color, Runnable onPress) {
            super(x, y, width, height, Component.empty(), button -> onPress.run(), DEFAULT_NARRATION);
            this.color = color;
        }

        @Override
        protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int border = isHovered() ? 0xFFFFFFFF : 0xFF202020;
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, border);
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF000000 | color);
        }
    }
}
