package net.shik.krepapi;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.shik.krepapi.net.KrepapiNetworking;
import net.shik.krepapi.server.KrepapiFabricServerNetworking;

/**
 * Loads {@link net.shik.krepapi.client.KrepapiClient} reflectively so entrypoints that only resolve
 * classes from the main jar (e.g. some loaders) still run client init.
 */
public class Krepapi implements ModInitializer, ClientModInitializer {

    private static final String CLIENT_INITIALIZER = "net.shik.krepapi.client.KrepapiClient";

    @Override
    public void onInitialize() {
        KrepapiNetworking.registerPayloadTypes();
        KrepapiFabricServerNetworking.register();
    }

    @Override
    public void onInitializeClient() {
        try {
            Class<?> client = Class.forName(CLIENT_INITIALIZER, true, Krepapi.class.getClassLoader());
            ((ClientModInitializer) client.getDeclaredConstructor().newInstance()).onInitializeClient();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("KrepAPI: failed to load client initializer", e);
        }
    }
}
