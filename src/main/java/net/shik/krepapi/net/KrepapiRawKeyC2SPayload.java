package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiRawKeyC2SPayload(int key, int scancode, byte glfwAction, int modifiers, int sequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiRawKeyC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(KrepapiChannels.C2S_RAW_KEY));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiRawKeyC2SPayload> CODEC =
            CustomPacketPayload.codec(KrepapiRawKeyC2SPayload::write, KrepapiRawKeyC2SPayload::new);

    public KrepapiRawKeyC2SPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(key());
        buf.writeVarInt(scancode());
        buf.writeByte(glfwAction());
        buf.writeVarInt(modifiers());
        buf.writeVarInt(sequence());
    }

    @Override
    public Type<KrepapiRawKeyC2SPayload> type() {
        return TYPE;
    }
}
