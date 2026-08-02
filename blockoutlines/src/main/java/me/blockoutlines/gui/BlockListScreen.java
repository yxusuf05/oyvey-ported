package me.blockoutlines.gui;

import me.blockoutlines.BlockOutlines;
import me.blockoutlines.config.BlockOutlinesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shows every tracked block with its colour and lets it be recoloured or removed. */
public class BlockListScreen extends Screen {
    private final @Nullable Screen parent;
    private BlockList list;

    public BlockListScreen(@Nullable Screen parent) {
        super(Component.literal("Tracked blocks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new BlockList(minecraft, width, height - 64, 32, 24);
        addRenderableWidget(list);
        refresh();

        addRenderableWidget(Button.builder(Component.literal("Add block..."),
                        button -> minecraft.setScreen(new BlockPickerScreen(this)))
                .bounds(width / 2 - 154, height - 27, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 4, height - 27, 150, 20).build());
    }

    private void refresh() {
        list.rebuild();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        if (BlockOutlines.config().blocks.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.literal("No blocks yet - use \"Add block...\"").withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        BlockOutlines.configChanged();
        minecraft.setScreen(parent);
    }

    private class BlockList extends ContainerObjectSelectionList<BlockList.Row> {
        BlockList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return 340;
        }

        void rebuild() {
            clearEntries();
            BlockOutlinesConfig config = BlockOutlines.config();
            for (String id : new ArrayList<>(config.blocks.keySet())) {
                addEntry(new Row(id));
            }
        }

        class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String id;
            private final Component name;
            private final Button colorButton;
            private final Button defaultButton;
            private final Button removeButton;

            Row(String id) {
                this.id = id;
                this.name = displayName(id);
                this.colorButton = Button.builder(Component.literal("Colour"), button -> {
                    BlockOutlinesConfig config = BlockOutlines.config();
                    int current = colorOf(config, id);
                    minecraft.setScreen(new ColorPickerScreen(BlockListScreen.this, name.getString(), current, color -> {
                        config.blocks.put(id, color);
                        BlockOutlines.configChanged();
                    }));
                }).size(60, 20).build();
                this.defaultButton = Button.builder(Component.literal("Default"), button -> {
                    BlockOutlines.config().blocks.put(id, -1);
                    BlockOutlines.configChanged();
                }).size(60, 20).build();
                this.removeButton = Button.builder(Component.literal("X"), button -> {
                    BlockOutlines.config().blocks.remove(id);
                    BlockOutlines.configChanged();
                    rebuild();
                }).size(20, 20).build();
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                BlockOutlinesConfig config = BlockOutlines.config();
                int right = getContentX() + getContentWidth();
                int y = getContentY();

                removeButton.setPosition(right - 20, y);
                removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
                defaultButton.setPosition(right - 84, y);
                defaultButton.render(guiGraphics, mouseX, mouseY, partialTick);
                colorButton.setPosition(right - 148, y);
                colorButton.render(guiGraphics, mouseX, mouseY, partialTick);

                int swatchX = right - 172;
                guiGraphics.fill(swatchX, y + 2, swatchX + 16, y + 18, 0xFF000000);
                guiGraphics.fill(swatchX + 1, y + 3, swatchX + 15, y + 17, colorOf(config, id));

                guiGraphics.drawString(font, name, getContentX(), y + 2, 0xFFFFFFFF);
                guiGraphics.drawString(font, Component.literal(id).withStyle(ChatFormatting.DARK_GRAY),
                        getContentX(), y + 12, 0xFFFFFFFF);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(colorButton, defaultButton, removeButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(colorButton, defaultButton, removeButton);
            }
        }
    }

    static int colorOf(BlockOutlinesConfig config, String id) {
        Integer color = config.blocks.get(id);
        return color == null || color == -1 ? config.searchColor : color;
    }

    /** Human readable block name, falling back to the raw id for blocks of unloaded mods. */
    static Component displayName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null) {
            return BuiltInRegistries.BLOCK.getOptional(identifier)
                    .<Component>map(block -> block.getName())
                    .orElse(Component.literal(id).withStyle(ChatFormatting.RED));
        }
        return Component.literal(id).withStyle(ChatFormatting.RED);
    }
}
