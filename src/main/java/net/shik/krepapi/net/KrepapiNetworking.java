package net.shik.krepapi.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers play-stage payload codecs on both sides.
 */
public final class KrepapiNetworking {
    private KrepapiNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundPlay().register(KrepapiHelloS2CPayload.ID, KrepapiHelloS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(KrepapiBindingsS2CPayload.ID, KrepapiBindingsS2CPayload.CODEC, 65536);

        PayloadTypeRegistry.serverboundPlay().register(KrepapiClientInfoC2SPayload.ID, KrepapiClientInfoC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KrepapiKeyActionC2SPayload.ID, KrepapiKeyActionC2SPayload.CODEC);
    }
}
