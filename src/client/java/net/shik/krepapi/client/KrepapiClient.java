package net.shik.krepapi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class KrepapiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KrepapiClientNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(ServerBindingManager::tick);
    }
}
