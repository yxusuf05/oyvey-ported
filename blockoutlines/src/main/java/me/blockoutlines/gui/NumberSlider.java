package me.blockoutlines.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** A slider over an arbitrary numeric range that prints "Label: value" on its face. */
public class NumberSlider extends AbstractSliderButton {
    private final String label;
    private final String suffix;
    private final double min;
    private final double max;
    private final double step;
    private final boolean integer;
    private final DoubleConsumer onChange;

    public NumberSlider(int width, int height, String label, String suffix, double value,
                        double min, double max, double step, boolean integer, DoubleConsumer onChange) {
        super(0, 0, width, height, Component.empty(), (value - min) / (max - min));
        this.label = label;
        this.suffix = suffix;
        this.min = min;
        this.max = max;
        this.step = step;
        this.integer = integer;
        this.onChange = onChange;
        updateMessage();
    }

    /** Moves the handle without firing {@code onChange}, used when the value changed elsewhere. */
    public void setNumber(double newValue) {
        this.value = (Math.max(min, Math.min(max, newValue)) - min) / (max - min);
        updateMessage();
    }

    public double currentValue() {
        double raw = min + this.value * (max - min);
        double snapped = Math.round(raw / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    @Override
    protected void updateMessage() {
        double current = currentValue();
        String text = integer
                ? Integer.toString((int) Math.round(current))
                : String.format(java.util.Locale.ROOT, "%.2f", current);
        setMessage(Component.literal(label + ": " + text + suffix));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentValue());
    }
}
