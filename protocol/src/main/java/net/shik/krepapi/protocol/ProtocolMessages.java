package net.shik.krepapi.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary layout shared by Fabric client, Fabric server, and Paper. Field order is stable per protocol version.
 */
public final class ProtocolMessages {

    public static final byte HELLO_FLAG_REQUIRE_RESPONSE = 1;

    /** Maximum binding rows in one {@link BindingsSync} packet (encode and decode). */
    public static final int MAX_BINDING_ENTRIES = 2048;

    /** Maximum UTF-8 byte length for {@link BindingEntry#actionId()} and {@link KeyAction#actionId()}. */
    public static final int MAX_ACTION_ID_UTF8_BYTES = 256;

    /** Maximum UTF-8 byte length for {@link BindingEntry#category()}. */
    public static final int MAX_CATEGORY_UTF8_BYTES = 256;

    /**
     * Hard cap on encoded {@link BindingsSync} size (bytes) to bound allocation on encode/decode.
     */
    private static final long MAX_BINDINGS_SYNC_ENCODED_BYTES = 50_000_000L;

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

    private static ByteBuffer allocateForEncode(long need, String context) {
        if (need < 0 || need > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException(context + ": encoded size out of range");
        }
        return ByteBuffer.allocate((int) need);
    }

    public static byte[] encodeHello(Hello msg) {
        long need = (long) ProtocolBuf.varIntEncodedSize(msg.protocolVersion())
                + 1L
                + (long) ProtocolBuf.utfEncodedSize(msg.minModVersion())
                + 8L;
        ByteBuffer buf = allocateForEncode(need, "hello");
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
        long need = (long) ProtocolBuf.varIntEncodedSize(msg.protocolVersion())
                + (long) ProtocolBuf.utfEncodedSize(msg.modVersion())
                + (long) ProtocolBuf.varIntEncodedSize(msg.capabilities())
                + 8L;
        ByteBuffer buf = allocateForEncode(need, "client_info");
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
        int n = msg.entries().size();
        if (n > MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("too many binding entries: " + n);
        }
        long need = ProtocolBuf.varIntEncodedSize(n);
        for (BindingEntry e : msg.entries()) {
            need += (long) ProtocolBuf.utfEncodedSize(e.actionId(), MAX_ACTION_ID_UTF8_BYTES);
            need += (long) ProtocolBuf.utfEncodedSize(e.displayName());
            need += (long) ProtocolBuf.varIntEncodedSize(e.defaultKey());
            need += 1L;
            need += (long) ProtocolBuf.utfEncodedSize(e.category(), MAX_CATEGORY_UTF8_BYTES);
        }
        if (need > MAX_BINDINGS_SYNC_ENCODED_BYTES) {
            throw new IllegalArgumentException("bindings payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "bindings_sync");
        ProtocolBuf.writeVarInt(buf, n);
        for (BindingEntry e : msg.entries()) {
            ProtocolBuf.writeUtf(buf, e.actionId(), MAX_ACTION_ID_UTF8_BYTES);
            ProtocolBuf.writeUtf(buf, e.displayName());
            ProtocolBuf.writeVarInt(buf, e.defaultKey());
            ProtocolBuf.writeByte(buf, e.overrideVanilla() ? 1 : 0);
            ProtocolBuf.writeUtf(buf, e.category(), MAX_CATEGORY_UTF8_BYTES);
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static BindingsSync decodeBindingsSync(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int n = ProtocolBuf.readVarInt(buf);
        if (n < 0 || n > MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("invalid binding count: " + n);
        }
        List<BindingEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = ProtocolBuf.readUtf(buf, MAX_ACTION_ID_UTF8_BYTES);
            String name = ProtocolBuf.readUtf(buf);
            int key = ProtocolBuf.readVarInt(buf);
            boolean ov = ProtocolBuf.readUnsignedByte(buf) != 0;
            String cat = ProtocolBuf.readUtf(buf, MAX_CATEGORY_UTF8_BYTES);
            list.add(new BindingEntry(id, name, key, ov, cat));
        }
        return new BindingsSync(List.copyOf(list));
    }

    public static byte[] encodeKeyAction(KeyAction msg) {
        long need = (long) ProtocolBuf.utfEncodedSize(msg.actionId(), MAX_ACTION_ID_UTF8_BYTES)
                + 1L
                + (long) ProtocolBuf.varIntEncodedSize(msg.sequence());
        ByteBuffer buf = allocateForEncode(need, "key_action");
        ProtocolBuf.writeUtf(buf, msg.actionId(), MAX_ACTION_ID_UTF8_BYTES);
        ProtocolBuf.writeByte(buf, msg.phase());
        ProtocolBuf.writeVarInt(buf, msg.sequence());
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static KeyAction decodeKeyAction(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        String id = ProtocolBuf.readUtf(buf, MAX_ACTION_ID_UTF8_BYTES);
        byte phase = (byte) ProtocolBuf.readUnsignedByte(buf);
        int seq = ProtocolBuf.readVarInt(buf);
        return new KeyAction(id, phase, seq);
    }
}
