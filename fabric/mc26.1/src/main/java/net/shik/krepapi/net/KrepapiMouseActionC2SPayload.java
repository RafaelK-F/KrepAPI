package net.shik.krepapi.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.ProtocolMessages;

public record KrepapiMouseActionC2SPayload(ProtocolMessages.MouseActionEvent action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KrepapiMouseActionC2SPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.parse(KrepapiChannels.C2S_MOUSE_ACTION));

    public static final StreamCodec<RegistryFriendlyByteBuf, KrepapiMouseActionC2SPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProtocolMessages.MouseActionEvent a = payload.action();
                buf.writeByte(a.kind());
                buf.writeVarInt(a.sequence());
                if (a.kind() == ProtocolMessages.MOUSE_ACTION_KIND_BUTTON) {
                    buf.writeByte(a.button());
                    buf.writeByte(a.glfwAction());
                    buf.writeVarInt(a.modifiers());
                } else if (a.kind() == ProtocolMessages.MOUSE_ACTION_KIND_SCROLL) {
                    buf.writeFloat(a.scrollDeltaX());
                    buf.writeFloat(a.scrollDeltaY());
                } else {
                    throw new IllegalArgumentException("unknown mouse action kind: " + a.kind());
                }
                buf.writeByte(a.extras());
                if ((a.extras() & ProtocolMessages.MOUSE_ACTION_EXTRA_HAS_CURSOR) != 0) {
                    buf.writeFloat(a.cursorX());
                    buf.writeFloat(a.cursorY());
                }
            },
            buf -> {
                byte kind = buf.readByte();
                int seq = buf.readVarInt();
                byte button = 0;
                byte glfwAction = 0;
                int modifiers = 0;
                float sx = 0f;
                float sy = 0f;
                if (kind == ProtocolMessages.MOUSE_ACTION_KIND_BUTTON) {
                    button = buf.readByte();
                    glfwAction = buf.readByte();
                    modifiers = buf.readVarInt();
                } else if (kind == ProtocolMessages.MOUSE_ACTION_KIND_SCROLL) {
                    sx = buf.readFloat();
                    sy = buf.readFloat();
                } else {
                    throw new IllegalArgumentException("unknown mouse action kind: " + kind);
                }
                byte extras = buf.readByte();
                float cx = 0f;
                float cy = 0f;
                if ((extras & ProtocolMessages.MOUSE_ACTION_EXTRA_HAS_CURSOR) != 0) {
                    cx = buf.readFloat();
                    cy = buf.readFloat();
                }
                return new KrepapiMouseActionC2SPayload(new ProtocolMessages.MouseActionEvent(
                        kind,
                        seq,
                        button,
                        glfwAction,
                        modifiers,
                        sx,
                        sy,
                        extras,
                        cx,
                        cy
                ));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
