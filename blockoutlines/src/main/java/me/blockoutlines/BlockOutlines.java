package me.blockoutlines;

import me.blockoutlines.config.BlockOutlinesConfig;
import me.blockoutlines.gui.ConfigScreen;
import me.blockoutlines.render.BlockScanner;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockOutlines implements ClientModInitializer {
    public static final String MOD_ID = "blockoutlines";
    public static final Logger LOGGER = LoggerFactory.getLogger("BlockOutlines");

    private static final BlockScanner SCANNER = new BlockScanner();
    private static BlockOutlinesConfig config = new BlockOutlinesConfig();

    public static BlockOutlinesConfig config() {
        return config;
    }

    public static BlockScanner scanner() {
        return SCANNER;
    }

    @Override
    public void onInitializeClient() {
        config = BlockOutlinesConfig.load();
        SCANNER.invalidate();
        LOGGER.info("[BlockOutlines] loaded, tracking {} block(s)", config.blocks.size());
    }

    /** Persists the config and drops cached scan results, called by every settings screen. */
    public static void configChanged() {
        SCANNER.invalidate();
        config.save();
    }

    /** Called from the keyboard mixin for every key press outside of a screen. */
    public static void onKeyPressed(int key) {
        if (key == -1) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (key == config.toggleKey) {
            config.enabled = !config.enabled;
            configChanged();
            if (config.toggleMessage && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Block Outlines ")
                        .append(config.enabled
                                ? Component.literal("on").withStyle(ChatFormatting.GREEN)
                                : Component.literal("off").withStyle(ChatFormatting.RED)), true);
            }
        } else if (key == config.menuKey) {
            minecraft.setScreen(new ConfigScreen(null));
        }
    }
}
