package net.shik.krepapi.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers play-stage payload codecs on both sides.
 */
public final class KrepapiNetworking {
    private KrepapiNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(KrepapiHelloS2CPayload.ID, KrepapiHelloS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().registerLarge(KrepapiBindingsS2CPayload.ID, KrepapiBindingsS2CPayload.CODEC, 65536);

        PayloadTypeRegistry.playC2S().register(KrepapiClientInfoC2SPayload.ID, KrepapiClientInfoC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KrepapiKeyActionC2SPayload.ID, KrepapiKeyActionC2SPayload.CODEC);
    }
}
