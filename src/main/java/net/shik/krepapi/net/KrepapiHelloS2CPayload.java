package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiHelloS2CPayload(int protocolVersion, byte flags, String minModVersion, long challengeNonce) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiHelloS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_HELLO);

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiHelloS2CPayload> CODEC = StreamCodec.of(
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

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
