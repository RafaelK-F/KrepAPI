package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiKeyActionC2SPayload(String actionId, byte phase, int sequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiKeyActionC2SPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.C2S_KEY_ACTION));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiKeyActionC2SPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.actionId(), ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES);
                buf.writeByte(payload.phase());
                buf.writeVarInt(payload.sequence());
            },
            buf -> new KrepapiKeyActionC2SPayload(
                    buf.readUtf(ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES),
                    buf.readByte(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
