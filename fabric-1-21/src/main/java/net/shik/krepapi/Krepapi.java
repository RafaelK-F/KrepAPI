package net.shik.krepapi;

import net.fabricmc.api.ModInitializer;
import net.shik.krepapi.net.KrepapiNetworking;
import net.shik.krepapi.server.KrepapiFabricServerNetworking;

public class Krepapi implements ModInitializer {

    @Override
    public void onInitialize() {
        KrepapiNetworking.registerPayloadTypes();
        KrepapiFabricServerNetworking.register();
    }
}
