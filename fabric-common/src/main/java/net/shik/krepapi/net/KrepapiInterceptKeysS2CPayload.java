package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiInterceptKeysS2CPayload(List<ProtocolMessages.InterceptEntry> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiInterceptKeysS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.S2C_INTERCEPT_KEYS));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiInterceptKeysS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                int n = payload.entries().size();
                if (n > ProtocolMessages.MAX_INTERCEPT_ENTRIES) {
                    throw new IllegalArgumentException("too many intercept entries: " + n);
                }
                buf.writeVarInt(n);
                for (ProtocolMessages.InterceptEntry e : payload.entries()) {
                    buf.writeVarInt(e.slotId());
                    buf.writeBoolean(e.blockVanilla());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > ProtocolMessages.MAX_INTERCEPT_ENTRIES) {
                    throw new IllegalArgumentException("invalid intercept count: " + n);
                }
                List<ProtocolMessages.InterceptEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new ProtocolMessages.InterceptEntry(buf.readVarInt(), buf.readBoolean()));
                }
                return new KrepapiInterceptKeysS2CPayload(List.copyOf(list));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
