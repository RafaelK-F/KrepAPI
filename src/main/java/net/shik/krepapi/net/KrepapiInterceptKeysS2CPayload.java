package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiInterceptKeysS2CPayload(List<ProtocolMessages.InterceptEntry> entries) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiInterceptKeysS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_INTERCEPT_KEYS);

    public static final PacketCodec<RegistryByteBuf, KrepapiInterceptKeysS2CPayload> CODEC = PacketCodec.ofStatic(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
