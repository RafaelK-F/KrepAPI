package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolBuf;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiBindingsS2CPayload(List<ProtocolMessages.BindingEntry> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiBindingsS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(KrepapiChannels.S2C_BINDINGS));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiBindingsS2CPayload> CODEC =
            CustomPacketPayload.codec(KrepapiBindingsS2CPayload::write, KrepapiBindingsS2CPayload::new);

    public KrepapiBindingsS2CPayload(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > ProtocolMessages.MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("invalid binding count: " + n);
        }
        List<ProtocolMessages.BindingEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ProtocolMessages.BindingEntry(
                    buf.readUtf(ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES),
                    buf.readUtf(ProtocolBuf.MAX_STRING),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(ProtocolMessages.MAX_CATEGORY_UTF8_BYTES)
            ));
        }
        this(List.copyOf(list));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        int n = entries().size();
        if (n > ProtocolMessages.MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("too many binding entries: " + n);
        }
        buf.writeVarInt(n);
        for (ProtocolMessages.BindingEntry e : entries()) {
            buf.writeUtf(e.actionId(), ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES);
            buf.writeUtf(e.displayName(), ProtocolBuf.MAX_STRING);
            buf.writeVarInt(e.defaultKey());
            buf.writeBoolean(e.overrideVanilla());
            buf.writeUtf(e.category(), ProtocolMessages.MAX_CATEGORY_UTF8_BYTES);
        }
    }

    @Override
    public Type<KrepapiBindingsS2CPayload> type() {
        return TYPE;
    }
}
