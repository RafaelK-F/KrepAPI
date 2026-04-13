package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiBindingsS2CPayload(ProtocolMessages.BindingsGridSync sync) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiBindingsS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.S2C_BINDINGS));

    private static final int MAX_BINDINGS_PACKET_BYTES =
            (int) Math.min(ProtocolMessages.MAX_BINDINGS_SYNC_ENCODED_BYTES, Integer.MAX_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiBindingsS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                byte[] enc = ProtocolMessages.encodeBindingsGridSync(payload.sync());
                buf.writeByteArray(enc);
            },
            buf -> {
                byte[] enc = buf.readByteArray(MAX_BINDINGS_PACKET_BYTES);
                return new KrepapiBindingsS2CPayload(ProtocolMessages.decodeBindingsGridSync(enc));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
