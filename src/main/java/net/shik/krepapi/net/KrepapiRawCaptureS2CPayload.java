package net.shik.krepapi.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiRawCaptureS2CPayload(ProtocolMessages.RawCaptureConfig config) implements CustomPayload {
    public static final CustomPayload.Id<KrepapiRawCaptureS2CPayload> ID = CustomPayload.id(KrepapiChannels.S2C_RAW_CAPTURE);

    public static final PacketCodec<RegistryByteBuf, KrepapiRawCaptureS2CPayload> CODEC = PacketCodec.ofStatic(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
