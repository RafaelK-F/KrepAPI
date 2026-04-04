package net.shik.krepapi.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiRawKeyC2SPayload(int key, int scancode, byte glfwAction, int modifiers, int sequence) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiRawKeyC2SPayload> ID = CustomPayload.id(KrepapiChannels.C2S_RAW_KEY);

    public static final PacketCodec<RegistryByteBuf, KrepapiRawKeyC2SPayload> CODEC = PacketCodec.ofStatic(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
