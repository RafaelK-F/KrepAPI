package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolBuf;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiBindingsS2CPayload(List<ProtocolMessages.BindingEntry> entries) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiBindingsS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_BINDINGS);

    public static final PacketCodec<RegistryByteBuf, KrepapiBindingsS2CPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                int n = payload.entries().size();
                if (n > ProtocolMessages.MAX_BINDING_ENTRIES) {
                    throw new IllegalArgumentException("too many binding entries: " + n);
                }
                buf.writeVarInt(n);
                for (ProtocolMessages.BindingEntry e : payload.entries()) {
                    buf.writeString(e.actionId(), ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES);
                    buf.writeString(e.displayName(), ProtocolBuf.MAX_STRING);
                    buf.writeVarInt(e.defaultKey());
                    buf.writeBoolean(e.overrideVanilla());
                    buf.writeString(e.category(), ProtocolMessages.MAX_CATEGORY_UTF8_BYTES);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > ProtocolMessages.MAX_BINDING_ENTRIES) {
                    throw new IllegalArgumentException("invalid binding count: " + n);
                }
                List<ProtocolMessages.BindingEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new ProtocolMessages.BindingEntry(
                            buf.readString(ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES),
                            buf.readString(ProtocolBuf.MAX_STRING),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readString(ProtocolMessages.MAX_CATEGORY_UTF8_BYTES)
                    ));
                }
                return new KrepapiBindingsS2CPayload(List.copyOf(list));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
