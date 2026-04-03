package net.shik.krepapi.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers play-stage payload codecs on both sides.
 */
public final class KrepapiNetworking {
    private KrepapiNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(KrepapiHelloS2CPayload.TYPE, KrepapiHelloS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().registerLarge(KrepapiBindingsS2CPayload.TYPE, KrepapiBindingsS2CPayload.CODEC, 65536);

        PayloadTypeRegistry.playC2S().register(KrepapiClientInfoC2SPayload.TYPE, KrepapiClientInfoC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KrepapiKeyActionC2SPayload.TYPE, KrepapiKeyActionC2SPayload.CODEC);
    }
}
