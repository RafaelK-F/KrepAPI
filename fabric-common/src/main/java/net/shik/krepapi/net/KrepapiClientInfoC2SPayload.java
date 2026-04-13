package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.WireHandshakeHeader;

public record KrepapiClientInfoC2SPayload(WireHandshakeHeader wire, String modVersion, int capabilities, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiClientInfoC2SPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.C2S_CLIENT_INFO));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiClientInfoC2SPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(KrepapiProtocolVersion.WIRE_MAGIC);
                buf.writeByte(payload.wire().schema());
                buf.writeByte(payload.wire().major());
                buf.writeByte(payload.wire().minor());
                buf.writeByte(payload.wire().patch());
                buf.writeUtf(payload.modVersion());
                buf.writeVarInt(payload.capabilities());
                buf.writeLong(payload.challengeNonce());
            },
            buf -> {
                byte magic = buf.readByte();
                if (magic != KrepapiProtocolVersion.WIRE_MAGIC) {
                    throw new IllegalArgumentException("unsupported client_info wire layout");
                }
                int schema = buf.readUnsignedByte();
                int maj = buf.readUnsignedByte();
                int min = buf.readUnsignedByte();
                int pat = buf.readUnsignedByte();
                return new KrepapiClientInfoC2SPayload(
                        new WireHandshakeHeader(schema, maj, min, pat),
                        buf.readUtf(),
                        buf.readVarInt(),
                        buf.readLong()
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
