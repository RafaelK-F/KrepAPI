package net.shik.krepapi.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiClientInfoC2SPayload(int protocolVersion, String modVersion, int capabilities, long challengeNonce) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiClientInfoC2SPayload> ID = CustomPayload.id(KrepapiChannels.C2S_CLIENT_INFO);

    public static final PacketCodec<RegistryByteBuf, KrepapiClientInfoC2SPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeVarInt(payload.protocolVersion());
                buf.writeString(payload.modVersion());
                buf.writeVarInt(payload.capabilities());
                buf.writeLong(payload.challengeNonce());
            },
            buf -> new KrepapiClientInfoC2SPayload(
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readLong()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
