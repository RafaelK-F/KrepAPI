package net.shik.krepapi.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary layout shared by Fabric client, Fabric server, and Paper. Field order is stable per protocol version.
 */
public final class ProtocolMessages {

    public static final byte HELLO_FLAG_REQUIRE_RESPONSE = 1;

    public record Hello(int protocolVersion, byte flags, String minModVersion, long challengeNonce) {
    }

    public record ClientInfo(int protocolVersion, String modVersion, int capabilities, long challengeNonce) {
    }

    public record BindingEntry(String actionId, String displayName, int defaultKey, boolean overrideVanilla, String category) {
    }

    public record BindingsSync(List<BindingEntry> entries) {
    }

    public record KeyAction(String actionId, byte phase, int sequence) {
        public static final byte PHASE_PRESS = 0;
        public static final byte PHASE_RELEASE = 1;
    }

    private ProtocolMessages() {
    }

    public static byte[] encodeHello(Hello msg) {
        ByteBuffer buf = ByteBuffer.allocate(512);
        ProtocolBuf.writeVarInt(buf, msg.protocolVersion());
        ProtocolBuf.writeByte(buf, msg.flags());
        ProtocolBuf.writeUtf(buf, msg.minModVersion());
        ProtocolBuf.writeLong(buf, msg.challengeNonce());
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static Hello decodeHello(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int pv = ProtocolBuf.readVarInt(buf);
        byte flags = (byte) ProtocolBuf.readUnsignedByte(buf);
        String min = ProtocolBuf.readUtf(buf);
        long nonce = ProtocolBuf.readLong(buf);
        return new Hello(pv, flags, min, nonce);
    }

    public static byte[] encodeClientInfo(ClientInfo msg) {
        ByteBuffer buf = ByteBuffer.allocate(512);
        ProtocolBuf.writeVarInt(buf, msg.protocolVersion());
        ProtocolBuf.writeUtf(buf, msg.modVersion());
        ProtocolBuf.writeVarInt(buf, msg.capabilities());
        ProtocolBuf.writeLong(buf, msg.challengeNonce());
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static ClientInfo decodeClientInfo(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int pv = ProtocolBuf.readVarInt(buf);
        String mv = ProtocolBuf.readUtf(buf);
        int cap = ProtocolBuf.readVarInt(buf);
        long nonce = ProtocolBuf.readLong(buf);
        return new ClientInfo(pv, mv, cap, nonce);
    }

    public static byte[] encodeBindingsSync(BindingsSync msg) {
        ByteBuffer buf = ByteBuffer.allocate(65536);
        ProtocolBuf.writeVarInt(buf, msg.entries().size());
        for (BindingEntry e : msg.entries()) {
            ProtocolBuf.writeUtf(buf, e.actionId());
            ProtocolBuf.writeUtf(buf, e.displayName());
            ProtocolBuf.writeVarInt(buf, e.defaultKey());
            ProtocolBuf.writeByte(buf, e.overrideVanilla() ? 1 : 0);
            ProtocolBuf.writeUtf(buf, e.category());
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static BindingsSync decodeBindingsSync(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int n = ProtocolBuf.readVarInt(buf);
        List<BindingEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = ProtocolBuf.readUtf(buf);
            String name = ProtocolBuf.readUtf(buf);
            int key = ProtocolBuf.readVarInt(buf);
            boolean ov = ProtocolBuf.readUnsignedByte(buf) != 0;
            String cat = ProtocolBuf.readUtf(buf);
            list.add(new BindingEntry(id, name, key, ov, cat));
        }
        return new BindingsSync(List.copyOf(list));
    }

    public static byte[] encodeKeyAction(KeyAction msg) {
        ByteBuffer buf = ByteBuffer.allocate(256);
        ProtocolBuf.writeUtf(buf, msg.actionId());
        ProtocolBuf.writeByte(buf, msg.phase());
        ProtocolBuf.writeVarInt(buf, msg.sequence());
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static KeyAction decodeKeyAction(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        String id = ProtocolBuf.readUtf(buf);
        byte phase = (byte) ProtocolBuf.readUnsignedByte(buf);
        int seq = ProtocolBuf.readVarInt(buf);
        return new KeyAction(id, phase, seq);
    }
}
