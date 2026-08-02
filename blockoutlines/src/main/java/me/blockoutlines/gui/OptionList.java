package me.blockoutlines.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Scrollable list of settings rows, one or two widgets wide. */
public class OptionList extends ContainerObjectSelectionList<OptionList.Row> {
    public static final int ROW_WIDTH = 320;
    private static final int GAP = 4;

    public OptionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    public void addHeader(Component text) {
        addEntry(new HeaderRow(text, minecraft));
    }

    public void addFull(AbstractWidget widget) {
        addEntry(new WidgetRow(List.of(widget)));
    }

    public void addPair(AbstractWidget left, AbstractWidget right) {
        addEntry(new WidgetRow(List.of(left, right)));
    }

    /** Row of widgets plus a colour swatch drawn at its right edge. */
    public void addWithSwatch(AbstractWidget widget, java.util.function.IntSupplier color) {
        addEntry(new SwatchRow(widget, color));
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    private static class WidgetRow extends Row {
        private final List<AbstractWidget> widgets;

        WidgetRow(List<AbstractWidget> widgets) {
            this.widgets = widgets;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int count = widgets.size();
            int width = (getContentWidth() - GAP * (count - 1)) / count;
            int x = getContentX();
            for (AbstractWidget widget : widgets) {
                widget.setWidth(width);
                widget.setPosition(x, getContentY());
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                x += width + GAP;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }

    private static class SwatchRow extends Row {
        private static final int SWATCH_WIDTH = 26;
        private final AbstractWidget widget;
        private final java.util.function.IntSupplier color;

        SwatchRow(AbstractWidget widget, java.util.function.IntSupplier color) {
            this.widget = widget;
            this.color = color;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int width = getContentWidth() - SWATCH_WIDTH - GAP;
            widget.setWidth(width);
            widget.setPosition(getContentX(), getContentY());
            widget.render(guiGraphics, mouseX, mouseY, partialTick);

            int x = getContentX() + width + GAP;
            int y = getContentY();
            int height = widget.getHeight();
            guiGraphics.fill(x, y, x + SWATCH_WIDTH, y + height, 0xFF000000);
            guiGraphics.fill(x + 1, y + 1, x + SWATCH_WIDTH - 1, y + height - 1, 0xFFFFFFFF);
            guiGraphics.fill(x + 2, y + 2, x + SWATCH_WIDTH - 2, y + height - 2, color.getAsInt());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }

    private static class HeaderRow extends Row {
        private final StringWidget text;

        HeaderRow(Component component, Minecraft minecraft) {
            this.text = new StringWidget(component, minecraft.font);
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            text.setPosition(getContentX(), getContentBottom() - text.getHeight() - 2);
            text.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.fill(getContentX(), getContentBottom() - 1, getContentX() + getContentWidth(),
                    getContentBottom(), 0x55FFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(text);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(text);
        }
    }
}
