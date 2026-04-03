package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiHelloS2CPayload(int protocolVersion, byte flags, String minModVersion, long challengeNonce) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiHelloS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_HELLO);

    public static final PacketCodec<RegistryFriendlyByteBuf, KrepapiHelloS2CPayload> CODEC = PacketCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.protocolVersion());
                buf.writeByte(payload.flags());
                buf.writeUtf(payload.minModVersion());
                buf.writeLong(payload.challengeNonce());
            },
            buf -> new KrepapiHelloS2CPayload(
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readUtf(),
                    buf.readLong()
            )
    );

    public static final CustomPayload.Type<KrepapiHelloS2CPayload> TYPE = new CustomPayload.Type<>(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
