package io.github.yxusuf05.skyloom.test;

import io.github.yxusuf05.skyloom.screen.SkyloomScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Boots a real client and opens the picker.
 * <p>
 * The point is the mixins. A hook whose signature does not match the Minecraft it is built against
 * fails while the class is being transformed, which happens long before this method runs, so
 * simply getting here proves the sky hooks fit the version. Worth running against every version
 * the mod claims to support, because the sky methods changed shape more than once.
 */
public class BootGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("skyloom")) throw new AssertionError("skyloom did not load");

        context.setScreen(SkyloomScreen::getInstance);
        context.waitTicks(20);
        context.takeScreenshot("picker");

        context.setScreen(() -> null);
        context.waitTicks(5);
    }
}
