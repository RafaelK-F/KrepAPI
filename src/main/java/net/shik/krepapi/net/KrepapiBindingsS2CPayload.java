package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiBindingsS2CPayload(List<ProtocolMessages.BindingEntry> entries) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiBindingsS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_BINDINGS);

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiBindingsS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entries().size());
                for (ProtocolMessages.BindingEntry e : payload.entries()) {
                    buf.writeUtf(e.actionId());
                    buf.writeUtf(e.displayName());
                    buf.writeVarInt(e.defaultKey());
                    buf.writeBoolean(e.overrideVanilla());
                    buf.writeUtf(e.category());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<ProtocolMessages.BindingEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new ProtocolMessages.BindingEntry(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readUtf()
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
