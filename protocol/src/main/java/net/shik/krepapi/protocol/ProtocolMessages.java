package net.shik.krepapi.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Binary layout shared by Fabric client, Fabric server, and Paper. Handshake payloads begin with a fixed
 * {@link KrepapiProtocolVersion#WIRE_MAGIC wire prefix}; field order after that prefix is stable for a given
 * {@link WireHandshakeHeader#schema() schema} and semver triple.
 */
public final class ProtocolMessages {

    public static final byte HELLO_FLAG_REQUIRE_RESPONSE = 1;

    /** Number of fixed category sections on the client (slots {@code 0} … {@code #GRID_CATEGORY_SLOTS - 1}). */
    public static final int GRID_CATEGORY_SLOTS = 10;

    /** Key rows per category (slots {@code 0} … {@code #GRID_KEYS_PER_CATEGORY - 1}). */
    public static final int GRID_KEYS_PER_CATEGORY = 32;

    /** Total server-driven {@link KeyMapping} pool size ({@link #GRID_CATEGORY_SLOTS} × {@link #GRID_KEYS_PER_CATEGORY}). */
    public static final int GRID_TOTAL_KEYS = GRID_CATEGORY_SLOTS * GRID_KEYS_PER_CATEGORY;

    /**
     * Linear index {@code 0 … GRID_TOTAL_KEYS - 1} for the client {@code KeyMapping} pool
     * ({@code categorySlot} * {@link #GRID_KEYS_PER_CATEGORY} + {@code keySlot}).
     */
    public static int flatGridIndex(int categorySlot, int keySlot) {
        return categorySlot * GRID_KEYS_PER_CATEGORY + keySlot;
    }

    /** First byte of {@link #encodeBindingsGridSync(BindingsGridSync)} payloads ({@code s2c_bindings}). */
    public static final byte BINDINGS_FORMAT_GRID_V1 = 2;

    /** Maximum cells in one {@link BindingsGridSync} packet (encode and decode). */
    public static final int MAX_GRID_CELLS = GRID_TOTAL_KEYS;

    /** Maximum UTF-8 byte length for {@link GridBindingCell#actionId()} and {@link KeyAction#actionId()}. */
    public static final int MAX_ACTION_ID_UTF8_BYTES = 256;

    /** Maximum UTF-8 byte length for category **titles** in {@link BindingsGridSync#categoryTitles()}. */
    public static final int MAX_CATEGORY_TITLE_UTF8_BYTES = 256;

    /** Maximum UTF-8 byte length for {@link GridBindingCell#lore()} (tooltip plain text; empty = no tooltip). */
    public static final int MAX_LORE_UTF8_BYTES = 8192;

    /**
     * Hard cap on encoded {@link BindingsGridSync} size (bytes). Encode and decode use the same limit.
     */
    public static final long MAX_BINDINGS_SYNC_ENCODED_BYTES = 50_000_000L;

    private static final long MAX_RAW_CAPTURE_ENCODED_BYTES = 50_000L;

    private static final long MAX_INTERCEPT_SYNC_ENCODED_BYTES = 4096L;

    private static final long MAX_MOUSE_CAPTURE_ENCODED_BYTES = 256L;

    private static final long MAX_MOUSE_ACTION_ENCODED_BYTES = 64L;

    /** Bit for {@link MouseCaptureConfig#flags()}: forward mouse button events. */
    public static final byte MOUSE_CAPTURE_BUTTONS = 1;
    /** Bit for {@link MouseCaptureConfig#flags()}: forward scroll events. */
    public static final byte MOUSE_CAPTURE_SCROLL = 2;
    /**
     * Bit for {@link MouseCaptureConfig#flags()}: client may set {@link MouseActionEvent#extras()}
     * {@link #MOUSE_ACTION_EXTRA_HAS_CURSOR} on {@code c2s_mouse_action}.
     */
    public static final byte MOUSE_CAPTURE_CURSOR_ON_EVENTS = 4;

    /** {@link MouseActionEvent#kind()} — mouse button press or release. */
    public static final byte MOUSE_ACTION_KIND_BUTTON = 0;
    /** {@link MouseActionEvent#kind()} — mouse wheel / trackpad scroll. */
    public static final byte MOUSE_ACTION_KIND_SCROLL = 1;

    /**
     * If set in {@link MouseActionEvent#extras()}, {@code cursorX} and {@code cursorY} are written after {@code extras}
     * (normalized 0–1 in window space).
     */
    public static final byte MOUSE_ACTION_EXTRA_HAS_CURSOR = 1;

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

    public record Hello(WireHandshakeHeader wire, byte flags, String minModVersion, long challengeNonce) {
    }

    public record ClientInfo(WireHandshakeHeader wire, String modVersion, int capabilities, long challengeNonce) {
    }

    /**
     * One occupied cell in the {@value #GRID_CATEGORY_SLOTS}×{@value #GRID_KEYS_PER_CATEGORY} server binding grid.
     */
    public record GridBindingCell(
            int categorySlot,
            int keySlot,
            String actionId,
            String displayName,
            int defaultKey,
            boolean overrideVanilla,
            String lore
    ) {
    }

    /**
     * Full {@code s2c_bindings} body: exactly {@value #GRID_CATEGORY_SLOTS} category titles (in slot order {@code 0…9};
     * empty string = no custom title) plus a sparse list of {@link GridBindingCell}s.
     */
    public record BindingsGridSync(List<String> categoryTitles, List<GridBindingCell> cells) {
    }

    /**
     * Collapses duplicate {@code (categorySlot, keySlot)}: only the last occurrence in {@code cells} is kept, preserving
     * the relative order of surviving cells (forward scan).
     */
    public static List<GridBindingCell> dedupeGridCellsLastWins(List<GridBindingCell> cells) {
        if (cells.size() <= 1) {
            return new ArrayList<>(cells);
        }
        HashMap<Long, Integer> lastIndex = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            GridBindingCell c = cells.get(i);
            long key = gridCellKey(c.categorySlot(), c.keySlot());
            lastIndex.put(key, i);
        }
        ArrayList<GridBindingCell> out = new ArrayList<>(lastIndex.size());
        for (int i = 0; i < cells.size(); i++) {
            GridBindingCell c = cells.get(i);
            long key = gridCellKey(c.categorySlot(), c.keySlot());
            if (lastIndex.get(key) == i) {
                out.add(c);
            }
        }
        return out;
    }

    private static long gridCellKey(int categorySlot, int keySlot) {
        return ((long) categorySlot << 32) ^ (keySlot & 0xffffffffL);
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

    /**
     * Server-driven mouse capture (S2C). When {@code enabled} is false, {@code flags} should be {@code 0}.
     * Combine {@link #MOUSE_CAPTURE_BUTTONS}, {@link #MOUSE_CAPTURE_SCROLL}, {@link #MOUSE_CAPTURE_CURSOR_ON_EVENTS}.
     */
    public record MouseCaptureConfig(boolean enabled, byte flags, boolean consumeVanilla) {
    }

    /**
     * Mouse input (C2S). Use {@link #MOUSE_ACTION_KIND_BUTTON} with GLFW button / action / modifiers, or
     * {@link #MOUSE_ACTION_KIND_SCROLL} with scroll deltas. Unused fields are zero.
     */
    public record MouseActionEvent(
            byte kind,
            int sequence,
            byte button,
            byte glfwAction,
            int modifiers,
            float scrollDeltaX,
            float scrollDeltaY,
            byte extras,
            float cursorX,
            float cursorY
    ) {
    }

    private ProtocolMessages() {
    }

    private static ByteBuffer allocateForEncode(long need, String context) {
        if (need < 0 || need > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException(context + ": encoded size out of range");
        }
        return ByteBuffer.allocate((int) need);
    }

    /** Rejects payloads with bytes after the last field (strict framing). */
    private static void requireFullyConsumed(ByteBuffer buf, String context) {
        if (buf.hasRemaining()) {
            throw new IllegalArgumentException("trailing bytes after " + context + ": " + buf.remaining());
        }
    }

    private static final int WIRE_PREFIX_BYTES = 5;

    private static void writeWirePrefix(ByteBuffer buf, WireHandshakeHeader w) {
        buf.put(KrepapiProtocolVersion.WIRE_MAGIC);
        buf.put((byte) w.schema());
        buf.put((byte) w.major());
        buf.put((byte) w.minor());
        buf.put((byte) w.patch());
    }

    private static WireHandshakeHeader readWirePrefix(ByteBuffer buf, String context) {
        if (buf.remaining() < WIRE_PREFIX_BYTES) {
            throw new IllegalArgumentException(context + ": expected wire prefix (" + WIRE_PREFIX_BYTES + " bytes)");
        }
        byte magic = buf.get();
        if (magic != KrepapiProtocolVersion.WIRE_MAGIC) {
            throw new IllegalArgumentException(context + ": unsupported wire layout (legacy client or corrupt packet)");
        }
        int schema = buf.get() & 0xFF;
        int maj = buf.get() & 0xFF;
        int min = buf.get() & 0xFF;
        int pat = buf.get() & 0xFF;
        return new WireHandshakeHeader(schema, maj, min, pat);
    }

    public static byte[] encodeHello(Hello msg) {
        long need = (long) WIRE_PREFIX_BYTES
                + 1L
                + (long) ProtocolBuf.utfEncodedSize(msg.minModVersion())
                + 8L;
        ByteBuffer buf = allocateForEncode(need, "hello");
        writeWirePrefix(buf, msg.wire());
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
        WireHandshakeHeader wire = readWirePrefix(buf, "hello");
        byte flags = (byte) ProtocolBuf.readUnsignedByte(buf);
        String min = ProtocolBuf.readUtf(buf);
        long nonce = ProtocolBuf.readLong(buf);
        requireFullyConsumed(buf, "hello");
        return new Hello(wire, flags, min, nonce);
    }

    public static byte[] encodeClientInfo(ClientInfo msg) {
        long need = (long) WIRE_PREFIX_BYTES
                + (long) ProtocolBuf.utfEncodedSize(msg.modVersion())
                + (long) ProtocolBuf.varIntEncodedSize(msg.capabilities())
                + 8L;
        ByteBuffer buf = allocateForEncode(need, "client_info");
        writeWirePrefix(buf, msg.wire());
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
        WireHandshakeHeader wire = readWirePrefix(buf, "client_info");
        String mv = ProtocolBuf.readUtf(buf);
        int cap = ProtocolBuf.readVarInt(buf);
        long nonce = ProtocolBuf.readLong(buf);
        requireFullyConsumed(buf, "client_info");
        return new ClientInfo(wire, mv, cap, nonce);
    }

    public static byte[] encodeBindingsGridSync(BindingsGridSync msg) {
        validateGridSync(msg);
        if (msg.cells().size() > MAX_GRID_CELLS) {
            throw new IllegalArgumentException("too many grid cells: " + msg.cells().size());
        }
        List<GridBindingCell> cells = dedupeGridCellsLastWins(msg.cells());
        long need = 1L;
        for (String t : msg.categoryTitles()) {
            need += (long) ProtocolBuf.utfEncodedSize(t, MAX_CATEGORY_TITLE_UTF8_BYTES);
        }
        need += (long) ProtocolBuf.varIntEncodedSize(cells.size());
        for (GridBindingCell e : cells) {
            need += 2L;
            need += (long) ProtocolBuf.utfEncodedSize(e.actionId(), MAX_ACTION_ID_UTF8_BYTES);
            need += (long) ProtocolBuf.utfEncodedSize(e.displayName());
            need += (long) ProtocolBuf.varIntEncodedSize(e.defaultKey());
            need += 1L;
            need += (long) ProtocolBuf.utfEncodedSize(e.lore(), MAX_LORE_UTF8_BYTES);
        }
        if (need > MAX_BINDINGS_SYNC_ENCODED_BYTES) {
            throw new IllegalArgumentException("bindings payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "bindings_grid");
        buf.put(BINDINGS_FORMAT_GRID_V1);
        for (String t : msg.categoryTitles()) {
            ProtocolBuf.writeUtf(buf, t, MAX_CATEGORY_TITLE_UTF8_BYTES);
        }
        ProtocolBuf.writeVarInt(buf, cells.size());
        for (GridBindingCell e : cells) {
            buf.put((byte) e.categorySlot());
            buf.put((byte) e.keySlot());
            ProtocolBuf.writeUtf(buf, e.actionId(), MAX_ACTION_ID_UTF8_BYTES);
            ProtocolBuf.writeUtf(buf, e.displayName());
            ProtocolBuf.writeVarInt(buf, e.defaultKey());
            ProtocolBuf.writeByte(buf, e.overrideVanilla() ? 1 : 0);
            ProtocolBuf.writeUtf(buf, e.lore(), MAX_LORE_UTF8_BYTES);
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static BindingsGridSync decodeBindingsGridSync(byte[] data) {
        if ((long) data.length > MAX_BINDINGS_SYNC_ENCODED_BYTES) {
            throw new IllegalArgumentException("bindings payload too large");
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (!buf.hasRemaining()) {
            throw new IllegalArgumentException("bindings: empty payload");
        }
        byte format = buf.get();
        if (format != BINDINGS_FORMAT_GRID_V1) {
            throw new IllegalArgumentException("unsupported bindings format: " + format);
        }
        List<String> titles = new ArrayList<>(GRID_CATEGORY_SLOTS);
        for (int i = 0; i < GRID_CATEGORY_SLOTS; i++) {
            titles.add(ProtocolBuf.readUtf(buf, MAX_CATEGORY_TITLE_UTF8_BYTES));
        }
        int n = ProtocolBuf.readVarInt(buf);
        if (n < 0 || n > MAX_GRID_CELLS) {
            throw new IllegalArgumentException("invalid grid cell count: " + n);
        }
        List<GridBindingCell> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int cat = buf.get() & 0xFF;
            int ks = buf.get() & 0xFF;
            if (cat >= GRID_CATEGORY_SLOTS || ks >= GRID_KEYS_PER_CATEGORY) {
                throw new IllegalArgumentException("invalid grid cell: categorySlot=" + cat + " keySlot=" + ks);
            }
            String id = ProtocolBuf.readUtf(buf, MAX_ACTION_ID_UTF8_BYTES);
            String name = ProtocolBuf.readUtf(buf);
            int key = ProtocolBuf.readVarInt(buf);
            boolean ov = ProtocolBuf.readUnsignedByte(buf) != 0;
            String lore = ProtocolBuf.readUtf(buf, MAX_LORE_UTF8_BYTES);
            list.add(new GridBindingCell(cat, ks, id, name, key, ov, lore));
        }
        requireFullyConsumed(buf, "bindings_grid");
        return new BindingsGridSync(List.copyOf(titles), List.copyOf(list));
    }

    private static void validateGridSync(BindingsGridSync msg) {
        if (msg.categoryTitles().size() != GRID_CATEGORY_SLOTS) {
            throw new IllegalArgumentException("categoryTitles must have length " + GRID_CATEGORY_SLOTS);
        }
        for (GridBindingCell c : msg.cells()) {
            if (c.categorySlot() < 0 || c.categorySlot() >= GRID_CATEGORY_SLOTS) {
                throw new IllegalArgumentException("invalid categorySlot: " + c.categorySlot());
            }
            if (c.keySlot() < 0 || c.keySlot() >= GRID_KEYS_PER_CATEGORY) {
                throw new IllegalArgumentException("invalid keySlot: " + c.keySlot());
            }
        }
    }

    public static byte[] encodeKeyAction(KeyAction msg) {
        requireValidKeyActionPhase(msg.phase());
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
        requireValidKeyActionPhase(phase);
        int seq = ProtocolBuf.readVarInt(buf);
        requireFullyConsumed(buf, "key_action");
        return new KeyAction(id, phase, seq);
    }

    private static void requireValidKeyActionPhase(byte phase) {
        if (phase != KeyAction.PHASE_PRESS && phase != KeyAction.PHASE_RELEASE) {
            throw new IllegalArgumentException("invalid key action phase: " + phase);
        }
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
        requireFullyConsumed(buf, "raw_capture");
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
        requireFullyConsumed(buf, "raw_key");
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
        requireFullyConsumed(buf, "intercept_keys");
        return new InterceptKeysSync(List.copyOf(list));
    }

    public static byte[] encodeMouseCaptureConfig(MouseCaptureConfig msg) {
        long need = 1L + 1L + 1L;
        if (need > MAX_MOUSE_CAPTURE_ENCODED_BYTES) {
            throw new IllegalArgumentException("mouse_capture payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "mouse_capture");
        ProtocolBuf.writeByte(buf, msg.enabled() ? 1 : 0);
        ProtocolBuf.writeByte(buf, msg.flags() & 0xFF);
        ProtocolBuf.writeByte(buf, msg.consumeVanilla() ? 1 : 0);
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static MouseCaptureConfig decodeMouseCaptureConfig(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        boolean enabled = ProtocolBuf.readUnsignedByte(buf) != 0;
        byte flags = (byte) ProtocolBuf.readUnsignedByte(buf);
        boolean consumeVanilla = ProtocolBuf.readUnsignedByte(buf) != 0;
        requireFullyConsumed(buf, "mouse_capture");
        return new MouseCaptureConfig(enabled, flags, consumeVanilla);
    }

    public static byte[] encodeMouseAction(MouseActionEvent msg) {
        long need = 1L + (long) ProtocolBuf.varIntEncodedSize(msg.sequence()) + 1L;
        if (msg.kind() == MOUSE_ACTION_KIND_BUTTON) {
            need += 1L + 1L + (long) ProtocolBuf.varIntEncodedSize(msg.modifiers());
        } else if (msg.kind() == MOUSE_ACTION_KIND_SCROLL) {
            need += 4L + 4L;
        } else {
            throw new IllegalArgumentException("unknown mouse action kind: " + msg.kind());
        }
        need += 1L;
        if ((msg.extras() & MOUSE_ACTION_EXTRA_HAS_CURSOR) != 0) {
            need += 4L + 4L;
        }
        if (need > MAX_MOUSE_ACTION_ENCODED_BYTES) {
            throw new IllegalArgumentException("mouse_action payload too large");
        }
        ByteBuffer buf = allocateForEncode(need, "mouse_action");
        ProtocolBuf.writeByte(buf, msg.kind());
        ProtocolBuf.writeVarInt(buf, msg.sequence());
        if (msg.kind() == MOUSE_ACTION_KIND_BUTTON) {
            ProtocolBuf.writeByte(buf, msg.button() & 0xFF);
            ProtocolBuf.writeByte(buf, msg.glfwAction() & 0xFF);
            ProtocolBuf.writeVarInt(buf, msg.modifiers());
        } else {
            ProtocolBuf.writeFloat(buf, msg.scrollDeltaX());
            ProtocolBuf.writeFloat(buf, msg.scrollDeltaY());
        }
        ProtocolBuf.writeByte(buf, msg.extras() & 0xFF);
        if ((msg.extras() & MOUSE_ACTION_EXTRA_HAS_CURSOR) != 0) {
            ProtocolBuf.writeFloat(buf, msg.cursorX());
            ProtocolBuf.writeFloat(buf, msg.cursorY());
        }
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    public static MouseActionEvent decodeMouseAction(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte kind = (byte) ProtocolBuf.readUnsignedByte(buf);
        int seq = ProtocolBuf.readVarInt(buf);
        byte button = 0;
        byte glfwAction = 0;
        int modifiers = 0;
        float sx = 0f;
        float sy = 0f;
        if (kind == MOUSE_ACTION_KIND_BUTTON) {
            button = (byte) ProtocolBuf.readUnsignedByte(buf);
            glfwAction = (byte) ProtocolBuf.readUnsignedByte(buf);
            modifiers = ProtocolBuf.readVarInt(buf);
        } else if (kind == MOUSE_ACTION_KIND_SCROLL) {
            sx = ProtocolBuf.readFloat(buf);
            sy = ProtocolBuf.readFloat(buf);
        } else {
            throw new IllegalArgumentException("unknown mouse action kind: " + kind);
        }
        byte extras = (byte) ProtocolBuf.readUnsignedByte(buf);
        float cx = 0f;
        float cy = 0f;
        if ((extras & MOUSE_ACTION_EXTRA_HAS_CURSOR) != 0) {
            cx = ProtocolBuf.readFloat(buf);
            cy = ProtocolBuf.readFloat(buf);
        }
        requireFullyConsumed(buf, "mouse_action");
        return new MouseActionEvent(kind, seq, button, glfwAction, modifiers, sx, sy, extras, cx, cy);
    }
}
