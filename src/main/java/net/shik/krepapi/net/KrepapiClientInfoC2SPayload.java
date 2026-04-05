package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiClientInfoC2SPayload(int protocolVersion, String modVersion, int capabilities, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiClientInfoC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(KrepapiChannels.C2S_CLIENT_INFO));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiClientInfoC2SPayload> CODEC =
            CustomPacketPayload.codec(KrepapiClientInfoC2SPayload::write, KrepapiClientInfoC2SPayload::new);

    public KrepapiClientInfoC2SPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readLong());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(protocolVersion());
        buf.writeUtf(modVersion());
        buf.writeVarInt(capabilities());
        buf.writeLong(challengeNonce());
    }

    @Override
    public Type<KrepapiClientInfoC2SPayload> type() {
        return TYPE;
    }
}
