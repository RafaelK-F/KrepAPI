package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;

public record KrepapiHelloS2CPayload(int protocolVersion, byte flags, String minModVersion, long challengeNonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiHelloS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(KrepapiChannels.S2C_HELLO));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiHelloS2CPayload> CODEC =
            CustomPacketPayload.codec(KrepapiHelloS2CPayload::write, KrepapiHelloS2CPayload::new);

    public KrepapiHelloS2CPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readByte(), buf.readUtf(), buf.readLong());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(protocolVersion());
        buf.writeByte(flags());
        buf.writeUtf(minModVersion());
        buf.writeLong(challengeNonce());
    }

    @Override
    public Type<KrepapiHelloS2CPayload> type() {
        return TYPE;
    }
}
