package net.shik.krepapi.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiKeyActionC2SPayload(String actionId, byte phase, int sequence) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiKeyActionC2SPayload> ID = CustomPayload.id(KrepapiChannels.C2S_KEY_ACTION);

    public static final PacketCodec<RegistryByteBuf, KrepapiKeyActionC2SPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeString(payload.actionId());
                buf.writeByte(payload.phase());
                buf.writeVarInt(payload.sequence());
            },
            buf -> new KrepapiKeyActionC2SPayload(buf.readString(), buf.readByte(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
