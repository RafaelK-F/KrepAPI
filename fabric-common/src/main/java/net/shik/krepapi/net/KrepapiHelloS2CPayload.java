package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.WireHandshakeHeader;

public record KrepapiHelloS2CPayload(WireHandshakeHeader wire, byte flags, String minModVersion, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiHelloS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.S2C_HELLO));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiHelloS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(KrepapiProtocolVersion.WIRE_MAGIC);
                buf.writeByte(payload.wire().schema());
                buf.writeByte(payload.wire().major());
                buf.writeByte(payload.wire().minor());
                buf.writeByte(payload.wire().patch());
                buf.writeByte(payload.flags());
                buf.writeUtf(payload.minModVersion());
                buf.writeLong(payload.challengeNonce());
            },
            buf -> {
                byte magic = buf.readByte();
                if (magic != KrepapiProtocolVersion.WIRE_MAGIC) {
                    throw new IllegalArgumentException("unsupported hello wire layout");
                }
                int schema = buf.readUnsignedByte();
                int maj = buf.readUnsignedByte();
                int min = buf.readUnsignedByte();
                int pat = buf.readUnsignedByte();
                return new KrepapiHelloS2CPayload(
                        new WireHandshakeHeader(schema, maj, min, pat),
                        buf.readByte(),
                        buf.readUtf(),
                        buf.readLong()
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
