package net.shik.krepapi.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;

public final class KrepapiClientNetworking {
    private KrepapiClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(KrepapiHelloS2CPayload.TYPE, (payload, context) -> {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("krepapi")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");
            int caps = KrepapiCapabilities.KEY_OVERRIDE | KrepapiCapabilities.RAW_KEYS;
            ClientPlayNetworking.send(new KrepapiClientInfoC2SPayload(
                    KrepapiProtocolVersion.CURRENT,
                    modVersion,
                    caps,
                    payload.challengeNonce()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(KrepapiBindingsS2CPayload.TYPE, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> ServerBindingManager.applyBindings(client, payload.entries()));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> ServerBindingManager.clear(client));
            KrepapiKeyPipeline.clearServerOverrides();
        });
    }
}
