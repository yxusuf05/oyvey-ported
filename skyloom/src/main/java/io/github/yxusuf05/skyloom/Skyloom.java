package io.github.yxusuf05.skyloom;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yxusuf05.skyloom.screen.SkyloomScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class Skyloom implements ClientModInitializer {
    public static final String MOD_ID = "skyloom";

    private static SkyloomConfig config = new SkyloomConfig();
    private static KeyMapping openKey;

    public static SkyloomConfig config() {
        return config;
    }

    public static void save() {
        config.save();
    }

    /**
     * The bind lives in the vanilla controls screen, so it can be changed there as well as
     * from inside the picker.
     */
    public static KeyMapping openKey() {
        return openKey;
    }

    @Override
    public void onInitializeClient() {
        config = SkyloomConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skyloom.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "skyloom"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null) client.setScreen(SkyloomScreen.getInstance());
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(Skyloom::save, "Skyloom config"));
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
