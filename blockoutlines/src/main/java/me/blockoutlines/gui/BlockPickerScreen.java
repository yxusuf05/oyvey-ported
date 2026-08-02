package me.blockoutlines.gui;

import me.blockoutlines.BlockOutlines;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable list of every registered block, one click adds it to the tracked blocks. */
public class BlockPickerScreen extends Screen {
    private final @Nullable Screen parent;
    private EditBox search;
    private BlockList list;

    public BlockPickerScreen(@Nullable Screen parent) {
        super(Component.literal("Add a block"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        search = new EditBox(font, width / 2 - 150, 26, 300, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search blocks...").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> list.filter(text));
        addRenderableWidget(search);
        setInitialFocus(search);

        list = new BlockList(minecraft, width, height - 90, 52, 20);
        addRenderableWidget(list);
        list.filter("");

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void add(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        BlockOutlines.config().blocks.putIfAbsent(id.toString(), -1);
        BlockOutlines.configChanged();
        onClose();
    }

    private class BlockList extends ObjectSelectionList<BlockList.Row> {
        BlockList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        void filter(String query) {
            clearEntries();
            String needle = query.toLowerCase(Locale.ROOT).trim();
            List<Row> rows = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (id == null || block.defaultBlockState().isAir()) {
                    continue;
                }
                String key = id.toString();
                String name = block.getName().getString();
                if (!needle.isEmpty()
                        && !key.toLowerCase(Locale.ROOT).contains(needle)
                        && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                rows.add(new Row(block, key, name));
            }
            rows.forEach(this::addEntry);
            setScrollAmount(0.0);
        }

        class Row extends ObjectSelectionList.Entry<Row> {
            private final Block block;
            private final String id;
            private final String name;
            private final ItemStack icon;

            Row(Block block, String id, String name) {
                this.block = block;
                this.id = id;
                this.name = name;
                this.icon = new ItemStack(block);
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int x = getContentX();
                int y = getContentY();
                if (!icon.isEmpty()) {
                    guiGraphics.renderFakeItem(icon, x, y);
                }
                boolean tracked = BlockOutlines.config().blocks.containsKey(id);
                guiGraphics.drawString(font, Component.literal(name), x + 22, y + 1, 0xFFFFFFFF);
                guiGraphics.drawString(font,
                        Component.literal(tracked ? id + " (already tracked)" : id)
                                .withStyle(tracked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                        x + 22, y + 11, 0xFFFFFFFF);
            }

            @Override
            public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
                BlockPickerScreen.this.add(block);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}
