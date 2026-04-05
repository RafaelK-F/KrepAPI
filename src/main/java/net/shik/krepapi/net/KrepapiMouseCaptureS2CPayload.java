package net.shik.krepapi.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiMouseCaptureS2CPayload(ProtocolMessages.MouseCaptureConfig config) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiMouseCaptureS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_MOUSE_CAPTURE);

    public static final PacketCodec<RegistryByteBuf, KrepapiMouseCaptureS2CPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                ProtocolMessages.MouseCaptureConfig c = payload.config();
                buf.writeBoolean(c.enabled());
                buf.writeByte(c.flags());
                buf.writeBoolean(c.consumeVanilla());
            },
            buf -> new KrepapiMouseCaptureS2CPayload(new ProtocolMessages.MouseCaptureConfig(
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readBoolean()
            ))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
