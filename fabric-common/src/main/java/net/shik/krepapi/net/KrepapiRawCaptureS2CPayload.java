package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiRawCaptureS2CPayload(ProtocolMessages.RawCaptureConfig config) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiRawCaptureS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.S2C_RAW_CAPTURE));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiRawCaptureS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProtocolMessages.RawCaptureConfig c = payload.config();
                buf.writeBoolean(c.enabled());
                buf.writeByte(c.mode());
                buf.writeBoolean(c.consumeVanilla());
                List<Integer> keys = c.whitelistKeys();
                int n = keys.size();
                if (n > ProtocolMessages.MAX_RAW_CAPTURE_KEYS) {
                    throw new IllegalArgumentException("too many raw capture keys: " + n);
                }
                buf.writeVarInt(n);
                for (int k : keys) {
                    buf.writeVarInt(k);
                }
            },
            buf -> {
                boolean enabled = buf.readBoolean();
                byte mode = buf.readByte();
                boolean consumeVanilla = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > ProtocolMessages.MAX_RAW_CAPTURE_KEYS) {
                    throw new IllegalArgumentException("invalid raw capture key count: " + n);
                }
                List<Integer> keys = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    keys.add(buf.readVarInt());
                }
                return new KrepapiRawCaptureS2CPayload(new ProtocolMessages.RawCaptureConfig(
                        enabled,
                        mode,
                        consumeVanilla,
                        List.copyOf(keys)
                ));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
