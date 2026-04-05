package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiMouseCaptureS2CPayload(ProtocolMessages.MouseCaptureConfig config) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiMouseCaptureS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(KrepapiChannels.S2C_MOUSE_CAPTURE));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiMouseCaptureS2CPayload> CODEC =
            CustomPacketPayload.codec(KrepapiMouseCaptureS2CPayload::write, KrepapiMouseCaptureS2CPayload::new);

    public KrepapiMouseCaptureS2CPayload(RegistryFriendlyByteBuf buf) {
        this(new ProtocolMessages.MouseCaptureConfig(buf.readBoolean(), buf.readByte(), buf.readBoolean()));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ProtocolMessages.MouseCaptureConfig c = config();
        buf.writeBoolean(c.enabled());
        buf.writeByte(c.flags());
        buf.writeBoolean(c.consumeVanilla());
    }

    @Override
    public Type<KrepapiMouseCaptureS2CPayload> type() {
        return TYPE;
    }
}
