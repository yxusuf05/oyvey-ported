package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingHudModule extends HudModule {
    public final Setting<Boolean> background = bool("Background", true);

    public PingHudModule() {
        super("Ping", "Displays your latency to the server", 40, 12);
    }

    private int ping() {
        if (nullCheck() || mc.player.connection == null) return 0;
        PlayerInfo info = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);

        String text = ping() + " ms";
        float pad = 5f;
        float height = mc.font.lineHeight + 5f;
        float width = mc.font.width(text) + pad + 5f;

        float x = getX();
        float y = getY();

        GuiGraphics context = e.getContext();
        if (background.getValue()) {
            HudUtil.panel(context, x, y, width, height);
        }
        context.drawString(mc.font, text,
                (int) (x + pad), (int) (y + (height - mc.font.lineHeight) / 2f + 1), -1);

        setWidth(width);
        setHeight(height);
    }
}
