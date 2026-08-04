package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

/**
 * Instantly respawns you the moment you die instead of waiting on the death screen.
 */
public class AutoRespawnModule extends Module {
    private final Timer timer = new Timer();

    public AutoRespawnModule() {
        super("AutoRespawn", "Respawns you instantly on death", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.connection == null) return;
        if (mc.player.isAlive()) return;
        if (!timer.passedMs(50)) return;

        mc.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        if (mc.screen instanceof DeathScreen) {
            mc.setScreen(null);
        }
        timer.reset();
    }
}
