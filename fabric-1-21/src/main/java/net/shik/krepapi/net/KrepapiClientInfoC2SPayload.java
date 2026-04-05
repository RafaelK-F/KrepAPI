package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiClientInfoC2SPayload(int protocolVersion, String modVersion, int capabilities, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiClientInfoC2SPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.C2S_CLIENT_INFO));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiClientInfoC2SPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.protocolVersion());
                buf.writeUtf(payload.modVersion());
                buf.writeVarInt(payload.capabilities());
                buf.writeLong(payload.challengeNonce());
            },
            buf -> new KrepapiClientInfoC2SPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readLong()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
