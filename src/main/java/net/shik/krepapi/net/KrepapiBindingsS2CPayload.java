package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiBindingsS2CPayload(List<ProtocolMessages.BindingEntry> entries) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiBindingsS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_BINDINGS);

    public static final PacketCodec<RegistryByteBuf, KrepapiBindingsS2CPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeVarInt(payload.entries().size());
                for (ProtocolMessages.BindingEntry e : payload.entries()) {
                    buf.writeString(e.actionId());
                    buf.writeString(e.displayName());
                    buf.writeVarInt(e.defaultKey());
                    buf.writeBoolean(e.overrideVanilla());
                    buf.writeString(e.category());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<ProtocolMessages.BindingEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new ProtocolMessages.BindingEntry(
                            buf.readString(),
                            buf.readString(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readString()
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
