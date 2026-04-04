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

    private static final long MAX_RAW_CAPTURE_ENCODED_BYTES = 50_000L;

    private static final long MAX_INTERCEPT_SYNC_ENCODED_BYTES = 4096L;

    /** {@link RawCaptureConfig#mode()} — capture off; whitelist ignored. */
    public static final byte RAW_CAPTURE_MODE_OFF = 0;
    /** Every key event is eligible (subject to client GUI rules). */
    public static final byte RAW_CAPTURE_MODE_ALL = 1;
    /** Only keys listed in {@link RawCaptureConfig#whitelistKeys()}. */
    public static final byte RAW_CAPTURE_MODE_WHITELIST = 2;

    /** Maximum GLFW key codes in a {@link RawCaptureConfig} whitelist. */
    public static final int MAX_RAW_CAPTURE_KEYS = 256;

    /** Maximum rows in {@link InterceptKeysSync}. */
    public static final int MAX_INTERCEPT_ENTRIES = 32;

    /** Pause / escape menu key slot. */
    public static final int INTERCEPT_SLOT_ESCAPE = 0;
    /** Debug HUD toggle slot. */
    public static final int INTERCEPT_SLOT_F3 = 1;
    /** Player list slot. */
    public static final int INTERCEPT_SLOT_TAB = 2;
    /** HUD visibility slot. */
    public static final int INTERCEPT_SLOT_F1 = 3;
    /** Perspective / reload slot (client handles both). */
    public static final int INTERCEPT_SLOT_F5 = 4;

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

    /**
     * Server-driven raw keyboard capture (S2C). When {@code enabled} is false, {@code mode} should be
     * {@link #RAW_CAPTURE_MODE_OFF} and the whitelist empty.
     */
    public record RawCaptureConfig(boolean enabled, byte mode, boolean consumeVanilla, List<Integer> whitelistKeys) {
    }

    /**
     * Raw keyboard event (C2S). {@code glfwAction} uses GLFW values: 0 release, 1 press, 2 repeat.
     */
    public record RawKeyEvent(int key, int scancode, byte glfwAction, int modifiers, int sequence) {
    }

    public record InterceptEntry(int slotId, boolean blockVanilla) {
    }

    /** Non-empty list activates intercept rules; empty list clears all intercepts on the client. */
    public record InterceptKeysSync(List<InterceptEntry> entries) {
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

    public static byte[] encodeRawCaptureConfig(RawCaptureConfig msg) {
        List<Integer> keys = msg.whitelistKeys();
        int n = keys.size();
        if (n > MAX_RAW_CAPTURE_KEYS) {
            throw new IllegalArgumentException("too many raw capture keys: " + n);
        }
        long need = 1L + 1L + 1L + (long) ProtocolBuf.varIntEncodedSize(n);
        for (int k : keys) {
            need += (long) ProtocolBuf.varIntEncodedSize(k);
        }
        if (need > MAX_RAW_CAPTURE_ENCODED_BYTES) {
            throw new IllegalArgumentException("raw_capture payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "raw_capture");
        ProtocolBuf.writeByte(buf, msg.enabled() ? 1 : 0);
        ProtocolBuf.writeByte(buf, msg.mode());
        ProtocolBuf.writeByte(buf, msg.consumeVanilla() ? 1 : 0);
        ProtocolBuf.writeVarInt(buf, n);
        for (int k : keys) {
            ProtocolBuf.writeVarInt(buf, k);
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static RawCaptureConfig decodeRawCaptureConfig(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        boolean enabled = ProtocolBuf.readUnsignedByte(buf) != 0;
        byte mode = (byte) ProtocolBuf.readUnsignedByte(buf);
        boolean consumeVanilla = ProtocolBuf.readUnsignedByte(buf) != 0;
        int n = ProtocolBuf.readVarInt(buf);
        if (n < 0 || n > MAX_RAW_CAPTURE_KEYS) {
            throw new IllegalArgumentException("invalid raw capture key count: " + n);
        }
        List<Integer> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add(ProtocolBuf.readVarInt(buf));
        }
        return new RawCaptureConfig(enabled, mode, consumeVanilla, List.copyOf(keys));
    }

    public static byte[] encodeRawKeyEvent(RawKeyEvent msg) {
        long need = (long) ProtocolBuf.varIntEncodedSize(msg.key())
                + (long) ProtocolBuf.varIntEncodedSize(msg.scancode())
                + 1L
                + (long) ProtocolBuf.varIntEncodedSize(msg.modifiers())
                + (long) ProtocolBuf.varIntEncodedSize(msg.sequence());
        ByteBuffer buf = allocateForEncode(need, "raw_key");
        ProtocolBuf.writeVarInt(buf, msg.key());
        ProtocolBuf.writeVarInt(buf, msg.scancode());
        ProtocolBuf.writeByte(buf, msg.glfwAction());
        ProtocolBuf.writeVarInt(buf, msg.modifiers());
        ProtocolBuf.writeVarInt(buf, msg.sequence());
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static RawKeyEvent decodeRawKeyEvent(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int key = ProtocolBuf.readVarInt(buf);
        int scancode = ProtocolBuf.readVarInt(buf);
        byte action = (byte) ProtocolBuf.readUnsignedByte(buf);
        int modifiers = ProtocolBuf.readVarInt(buf);
        int seq = ProtocolBuf.readVarInt(buf);
        return new RawKeyEvent(key, scancode, action, modifiers, seq);
    }

    public static byte[] encodeInterceptKeysSync(InterceptKeysSync msg) {
        int n = msg.entries().size();
        if (n > MAX_INTERCEPT_ENTRIES) {
            throw new IllegalArgumentException("too many intercept entries: " + n);
        }
        long need = (long) ProtocolBuf.varIntEncodedSize(n);
        for (InterceptEntry e : msg.entries()) {
            need += (long) ProtocolBuf.varIntEncodedSize(e.slotId());
            need += 1L;
        }
        if (need > MAX_INTERCEPT_SYNC_ENCODED_BYTES) {
            throw new IllegalArgumentException("intercept_keys payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "intercept_keys");
        ProtocolBuf.writeVarInt(buf, n);
        for (InterceptEntry e : msg.entries()) {
            ProtocolBuf.writeVarInt(buf, e.slotId());
            ProtocolBuf.writeByte(buf, e.blockVanilla() ? 1 : 0);
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static InterceptKeysSync decodeInterceptKeysSync(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int n = ProtocolBuf.readVarInt(buf);
        if (n < 0 || n > MAX_INTERCEPT_ENTRIES) {
            throw new IllegalArgumentException("invalid intercept count: " + n);
        }
        List<InterceptEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slot = ProtocolBuf.readVarInt(buf);
            boolean block = ProtocolBuf.readUnsignedByte(buf) != 0;
            list.add(new InterceptEntry(slot, block));
        }
        return new InterceptKeysSync(List.copyOf(list));
    }
}
