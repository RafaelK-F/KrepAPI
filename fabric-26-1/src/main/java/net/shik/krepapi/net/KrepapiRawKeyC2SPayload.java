package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiRawKeyC2SPayload(int key, int scancode, byte glfwAction, int modifiers, int sequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiRawKeyC2SPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.C2S_RAW_KEY));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiRawKeyC2SPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.key());
                buf.writeVarInt(payload.scancode());
                buf.writeByte(payload.glfwAction());
                buf.writeVarInt(payload.modifiers());
                buf.writeVarInt(payload.sequence());
            },
            buf -> new KrepapiRawKeyC2SPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
