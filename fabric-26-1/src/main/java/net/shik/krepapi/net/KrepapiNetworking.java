package net.shik.krepapi.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers play-stage payload codecs on both sides.
 */
public final class KrepapiNetworking {
    private KrepapiNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundPlay().register(KrepapiHelloS2CPayload.TYPE, KrepapiHelloS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(KrepapiBindingsS2CPayload.TYPE, KrepapiBindingsS2CPayload.CODEC, 65536);
        PayloadTypeRegistry.clientboundPlay().register(KrepapiRawCaptureS2CPayload.TYPE, KrepapiRawCaptureS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KrepapiInterceptKeysS2CPayload.TYPE, KrepapiInterceptKeysS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KrepapiMouseCaptureS2CPayload.TYPE, KrepapiMouseCaptureS2CPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(KrepapiClientInfoC2SPayload.TYPE, KrepapiClientInfoC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KrepapiKeyActionC2SPayload.TYPE, KrepapiKeyActionC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KrepapiRawKeyC2SPayload.TYPE, KrepapiRawKeyC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KrepapiMouseActionC2SPayload.TYPE, KrepapiMouseActionC2SPayload.CODEC);
    }
}
