package net.shik.krepapi.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.net.KrepapiInterceptKeysS2CPayload;
import net.shik.krepapi.net.KrepapiMouseCaptureS2CPayload;
import net.shik.krepapi.net.KrepapiRawCaptureS2CPayload;
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiClientNetworking {
    private KrepapiClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(KrepapiHelloS2CPayload.TYPE, (payload, context) -> {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("krepapi")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");
            String serverAddress = "";
            if (context.client().getCurrentServer() != null) {
                serverAddress = context.client().getCurrentServer().ip;
            }
            KrepapiDebugLog.beginSession(serverAddress, modVersion, KrepapiProtocolVersion.CURRENT);
            int caps = KrepapiCapabilities.KEY_OVERRIDE
                    | KrepapiCapabilities.RAW_KEYS
                    | KrepapiCapabilities.SERVER_RAW_CAPTURE
                    | KrepapiCapabilities.INTERCEPT_KEYS
                    | KrepapiCapabilities.SERVER_MOUSE_CAPTURE;
            KrepapiDebugLog.handshakeSent(KrepapiProtocolVersion.CURRENT, caps, modVersion);
            ClientPlayNetworking.send(new KrepapiClientInfoC2SPayload(
                    KrepapiProtocolVersion.CURRENT,
                    modVersion,
                    caps,
                    payload.challengeNonce()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(KrepapiBindingsS2CPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> ServerBindingManager.applyBindings(client, payload.entries()));
        });

        ClientPlayNetworking.registerGlobalReceiver(KrepapiRawCaptureS2CPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> RawCaptureState.apply(payload.config()));
        });

        ClientPlayNetworking.registerGlobalReceiver(KrepapiInterceptKeysS2CPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> InterceptKeyState.apply(new ProtocolMessages.InterceptKeysSync(payload.entries())));
        });

        ClientPlayNetworking.registerGlobalReceiver(KrepapiMouseCaptureS2CPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> MouseCaptureState.apply(payload.config()));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            KrepapiDebugLog.endSession("disconnect");
            client.execute(() -> ServerBindingManager.clear(client));
            RawCaptureState.clear();
            MouseCaptureState.clear();
            InterceptKeyState.clear();
        });
    }
}
