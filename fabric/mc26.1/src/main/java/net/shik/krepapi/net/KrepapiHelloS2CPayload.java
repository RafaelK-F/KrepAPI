package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiHelloS2CPayload(int protocolVersion, byte flags, String minModVersion, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiHelloS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.S2C_HELLO));

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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
