package dev.smoothinv;

import net.fabricmc.api.ClientModInitializer;

public class SmoothInv implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmoothInvConfig.load();
    }
}
