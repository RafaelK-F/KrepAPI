package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProtocolMessagesTest {

    @Test
    void helloRoundTripWithMaxLengthMinModVersion() {
        String longVer = "x".repeat(ProtocolBuf.MAX_STRING);
        ProtocolMessages.Hello msg = new ProtocolMessages.Hello(KrepapiProtocolVersion.CURRENT_WIRE, (byte) 0, longVer, 42L);
        byte[] enc = ProtocolMessages.encodeHello(msg);
        ProtocolMessages.Hello dec = ProtocolMessages.decodeHello(enc);
        assertEquals(KrepapiProtocolVersion.CURRENT_WIRE, dec.wire());
        assertEquals(longVer, dec.minModVersion());
        assertEquals(42L, dec.challengeNonce());
    }

    @Test
    void clientInfoRoundTripWithMaxLengthModVersion() {
        String longVer = "y".repeat(ProtocolBuf.MAX_STRING);
        ProtocolMessages.ClientInfo msg = new ProtocolMessages.ClientInfo(KrepapiProtocolVersion.CURRENT_WIRE, longVer, 3, -1L);
        byte[] enc = ProtocolMessages.encodeClientInfo(msg);
        ProtocolMessages.ClientInfo dec = ProtocolMessages.decodeClientInfo(enc);
        assertEquals(KrepapiProtocolVersion.CURRENT_WIRE, dec.wire());
        assertEquals(longVer, dec.modVersion());
    }

    @Test
    void decodeClientInfoRejectsTrailingBytes() {
        byte[] enc = ProtocolMessages.encodeClientInfo(
                new ProtocolMessages.ClientInfo(KrepapiProtocolVersion.CURRENT_WIRE, "mod", 0, 1L));
        byte[] padded = Arrays.copyOf(enc, enc.length + 1);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeClientInfo(padded));
    }

    @Test
    void decodeHelloRejectsLegacyVarintOnlyLayout() {
        ByteBuffer buf = ByteBuffer.allocate(512);
        ProtocolBuf.writeVarInt(buf, 3);
        ProtocolBuf.writeByte(buf, (byte) 0);
        ProtocolBuf.writeUtf(buf, "1.0.0");
        ProtocolBuf.writeLong(buf, 1L);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeHello(data));
    }

    @Test
    void decodeKeyActionRejectsTrailingBytes() {
        byte[] enc = ProtocolMessages.encodeKeyAction(new ProtocolMessages.KeyAction("x", ProtocolMessages.KeyAction.PHASE_PRESS, 9));
        byte[] padded = Arrays.copyOf(enc, enc.length + 2);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeKeyAction(padded));
    }

    @Test
    void decodeBindingsGridRejectsExcessiveCellCount() {
        ByteBuffer buf = ByteBuffer.allocate(400);
        buf.put(ProtocolMessages.BINDINGS_FORMAT_GRID_V1);
        for (int i = 0; i < ProtocolMessages.GRID_CATEGORY_SLOTS; i++) {
            ProtocolBuf.writeUtf(buf, "", ProtocolMessages.MAX_CATEGORY_TITLE_UTF8_BYTES);
        }
        ProtocolBuf.writeVarInt(buf, ProtocolMessages.MAX_GRID_CELLS + 1);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeBindingsGridSync(data));
    }

    @Test
    void encodeBindingsGridRejectsTooManyCells() {
        List<ProtocolMessages.GridBindingCell> cells = new ArrayList<>();
        for (int i = 0; i < ProtocolMessages.MAX_GRID_CELLS + 1; i++) {
            cells.add(new ProtocolMessages.GridBindingCell(0, 0, "a", "b", 0, false, ""));
        }
        List<String> titles = new ArrayList<>(Collections.nCopies(ProtocolMessages.GRID_CATEGORY_SLOTS, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolMessages.encodeBindingsGridSync(new ProtocolMessages.BindingsGridSync(titles, cells))
        );
    }

    @Test
    void dedupeGridCellsLastWinsKeepsLastPerCellInScanOrder() {
        ProtocolMessages.GridBindingCell x0 = new ProtocolMessages.GridBindingCell(0, 0, "x", "first", 1, false, "");
        ProtocolMessages.GridBindingCell y = new ProtocolMessages.GridBindingCell(1, 0, "y", "y", 2, false, "");
        ProtocolMessages.GridBindingCell x1 = new ProtocolMessages.GridBindingCell(0, 0, "x", "second", 3, true, "tip");
        List<ProtocolMessages.GridBindingCell> in = List.of(x0, y, x1);
        List<ProtocolMessages.GridBindingCell> out = ProtocolMessages.dedupeGridCellsLastWins(in);
        assertEquals(2, out.size());
        assertEquals(y, out.get(0));
        assertEquals(x1, out.get(1));
        assertEquals("second", out.get(1).displayName());
        assertEquals(3, out.get(1).defaultKey());
        assertEquals(true, out.get(1).overrideVanilla());
        assertEquals("tip", out.get(1).lore());
    }

    @Test
    void keyActionActionIdTooLongRejectedOnEncode() {
        String id = "i".repeat(ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES + 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolMessages.encodeKeyAction(new ProtocolMessages.KeyAction(id, (byte) 0, 1))
        );
    }

    @Test
    void decodeKeyActionRejectsInvalidPhase() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        ProtocolBuf.writeUtf(buf, "a", ProtocolMessages.MAX_ACTION_ID_UTF8_BYTES);
        ProtocolBuf.writeByte(buf, (byte) 7);
        ProtocolBuf.writeVarInt(buf, 1);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeKeyAction(data));
    }

    @Test
    void encodeKeyActionRejectsInvalidPhase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolMessages.encodeKeyAction(new ProtocolMessages.KeyAction("a", (byte) 2, 1))
        );
    }

    @Test
    void keyActionRoundTripPressAndRelease() {
        ProtocolMessages.KeyAction press = new ProtocolMessages.KeyAction("open_menu", ProtocolMessages.KeyAction.PHASE_PRESS, 1);
        byte[] encPress = ProtocolMessages.encodeKeyAction(press);
        ProtocolMessages.KeyAction decPress = ProtocolMessages.decodeKeyAction(encPress);
        assertEquals("open_menu", decPress.actionId());
        assertEquals(ProtocolMessages.KeyAction.PHASE_PRESS, decPress.phase());
        assertEquals(1, decPress.sequence());

        ProtocolMessages.KeyAction release = new ProtocolMessages.KeyAction("open_menu", ProtocolMessages.KeyAction.PHASE_RELEASE, 2);
        byte[] encRel = ProtocolMessages.encodeKeyAction(release);
        ProtocolMessages.KeyAction decRel = ProtocolMessages.decodeKeyAction(encRel);
        assertEquals(ProtocolMessages.KeyAction.PHASE_RELEASE, decRel.phase());
        assertEquals(2, decRel.sequence());
    }

    @Test
    void readUtfRespectsMaxSegmentLength() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        ProtocolBuf.writeUtf(buf, "hello", 10);
        buf.flip();
        assertEquals("hello", ProtocolBuf.readUtf(buf, 10));
    }

    @Test
    void readUtfRejectsWhenSegmentExceedsMax() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        ProtocolBuf.writeUtf(buf, "toolong");
        buf.flip();
        assertThrows(IllegalArgumentException.class, () -> ProtocolBuf.readUtf(buf, 3));
    }

    @Test
    void bindingsGridRoundTripAtMaxCellCount() {
        List<String> titles = new ArrayList<>(Collections.nCopies(ProtocolMessages.GRID_CATEGORY_SLOTS, ""));
        List<ProtocolMessages.GridBindingCell> cells = new ArrayList<>();
        for (int i = 0; i < ProtocolMessages.MAX_GRID_CELLS; i++) {
            int cat = i / ProtocolMessages.GRID_KEYS_PER_CATEGORY;
            int ks = i % ProtocolMessages.GRID_KEYS_PER_CATEGORY;
            cells.add(new ProtocolMessages.GridBindingCell(
                    cat,
                    ks,
                    "id" + i,
                    "name",
                    i,
                    false,
                    ""
            ));
        }
        byte[] enc = ProtocolMessages.encodeBindingsGridSync(new ProtocolMessages.BindingsGridSync(titles, cells));
        ProtocolMessages.BindingsGridSync dec = ProtocolMessages.decodeBindingsGridSync(enc);
        assertEquals(ProtocolMessages.MAX_GRID_CELLS, dec.cells().size());
        assertEquals("id0", dec.cells().getFirst().actionId());
    }

    @Test
    void bindingsGridRoundTripWithNonEmptyLore() {
        List<String> titles = new ArrayList<>(Collections.nCopies(ProtocolMessages.GRID_CATEGORY_SLOTS, "T"));
        var cells = List.of(
                new ProtocolMessages.GridBindingCell(0, 0, "a", "A", 65, false, ""),
                new ProtocolMessages.GridBindingCell(0, 1, "b", "B", 66, true, "Line one\nLine two"));
        byte[] enc = ProtocolMessages.encodeBindingsGridSync(new ProtocolMessages.BindingsGridSync(titles, cells));
        ProtocolMessages.BindingsGridSync dec = ProtocolMessages.decodeBindingsGridSync(enc);
        assertEquals(2, dec.cells().size());
        assertEquals("", dec.cells().get(0).lore());
        assertEquals("Line one\nLine two", dec.cells().get(1).lore());
        assertEquals("T", dec.categoryTitles().get(0));
    }

    @Test
    void rawCaptureConfigRoundTrip() {
        var msg = new ProtocolMessages.RawCaptureConfig(
                true,
                ProtocolMessages.RAW_CAPTURE_MODE_WHITELIST,
                true,
                java.util.List.of(32, 256, 341)
        );
        byte[] enc = ProtocolMessages.encodeRawCaptureConfig(msg);
        ProtocolMessages.RawCaptureConfig dec = ProtocolMessages.decodeRawCaptureConfig(enc);
        assertEquals(true, dec.enabled());
        assertEquals(ProtocolMessages.RAW_CAPTURE_MODE_WHITELIST, dec.mode());
        assertEquals(true, dec.consumeVanilla());
        assertEquals(java.util.List.of(32, 256, 341), dec.whitelistKeys());
    }

    @Test
    void rawKeyEventRoundTrip() {
        var ev = new ProtocolMessages.RawKeyEvent(65, 30, (byte) 1, 2, 99);
        byte[] enc = ProtocolMessages.encodeRawKeyEvent(ev);
        ProtocolMessages.RawKeyEvent dec = ProtocolMessages.decodeRawKeyEvent(enc);
        assertEquals(65, dec.key());
        assertEquals(30, dec.scancode());
        assertEquals((byte) 1, dec.glfwAction());
        assertEquals(2, dec.modifiers());
        assertEquals(99, dec.sequence());
    }

    @Test
    void interceptKeysSyncRoundTrip() {
        var sync = new ProtocolMessages.InterceptKeysSync(java.util.List.of(
                new ProtocolMessages.InterceptEntry(ProtocolMessages.INTERCEPT_SLOT_ESCAPE, true),
                new ProtocolMessages.InterceptEntry(ProtocolMessages.INTERCEPT_SLOT_F3, false)
        ));
        byte[] enc = ProtocolMessages.encodeInterceptKeysSync(sync);
        ProtocolMessages.InterceptKeysSync dec = ProtocolMessages.decodeInterceptKeysSync(enc);
        assertEquals(2, dec.entries().size());
        assertEquals(ProtocolMessages.INTERCEPT_SLOT_ESCAPE, dec.entries().getFirst().slotId());
        assertEquals(true, dec.entries().getFirst().blockVanilla());
    }

    @Test
    void encodeRawCaptureRejectsTooManyKeys() {
        java.util.List<Integer> keys = new java.util.ArrayList<>();
        for (int i = 0; i < ProtocolMessages.MAX_RAW_CAPTURE_KEYS + 1; i++) {
            keys.add(i);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolMessages.encodeRawCaptureConfig(
                        new ProtocolMessages.RawCaptureConfig(true, ProtocolMessages.RAW_CAPTURE_MODE_WHITELIST, false, keys)
                )
        );
    }

    @Test
    void mouseCaptureConfigRoundTrip() {
        var msg = new ProtocolMessages.MouseCaptureConfig(
                true,
                (byte) (ProtocolMessages.MOUSE_CAPTURE_BUTTONS | ProtocolMessages.MOUSE_CAPTURE_SCROLL),
                true
        );
        byte[] enc = ProtocolMessages.encodeMouseCaptureConfig(msg);
        ProtocolMessages.MouseCaptureConfig dec = ProtocolMessages.decodeMouseCaptureConfig(enc);
        assertEquals(true, dec.enabled());
        assertEquals(
                ProtocolMessages.MOUSE_CAPTURE_BUTTONS | ProtocolMessages.MOUSE_CAPTURE_SCROLL,
                dec.flags()
        );
        assertEquals(true, dec.consumeVanilla());
    }

    @Test
    void mouseActionButtonRoundTrip() {
        var ev = new ProtocolMessages.MouseActionEvent(
                ProtocolMessages.MOUSE_ACTION_KIND_BUTTON,
                7,
                (byte) 0,
                (byte) 1,
                2,
                0f,
                0f,
                (byte) 0,
                0f,
                0f
        );
        byte[] enc = ProtocolMessages.encodeMouseAction(ev);
        ProtocolMessages.MouseActionEvent dec = ProtocolMessages.decodeMouseAction(enc);
        assertEquals(ProtocolMessages.MOUSE_ACTION_KIND_BUTTON, dec.kind());
        assertEquals(7, dec.sequence());
        assertEquals((byte) 0, dec.button());
        assertEquals((byte) 1, dec.glfwAction());
        assertEquals(2, dec.modifiers());
    }

    @Test
    void mouseActionScrollWithCursorRoundTrip() {
        var ev = new ProtocolMessages.MouseActionEvent(
                ProtocolMessages.MOUSE_ACTION_KIND_SCROLL,
                42,
                (byte) 0,
                (byte) 0,
                0,
                1.5f,
                -0.25f,
                ProtocolMessages.MOUSE_ACTION_EXTRA_HAS_CURSOR,
                0.25f,
                0.75f
        );
        byte[] enc = ProtocolMessages.encodeMouseAction(ev);
        ProtocolMessages.MouseActionEvent dec = ProtocolMessages.decodeMouseAction(enc);
        assertEquals(ProtocolMessages.MOUSE_ACTION_KIND_SCROLL, dec.kind());
        assertEquals(42, dec.sequence());
        assertEquals(1.5f, dec.scrollDeltaX(), 0.0001f);
        assertEquals(-0.25f, dec.scrollDeltaY(), 0.0001f);
        assertEquals(0.25f, dec.cursorX(), 0.0001f);
        assertEquals(0.75f, dec.cursorY(), 0.0001f);
    }

    @Test
    void decodeMouseActionRejectsUnknownKind() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        ProtocolBuf.writeByte(buf, 99);
        ProtocolBuf.writeVarInt(buf, 1);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeMouseAction(data));
    }

    @Test
    void protocolBufFloatRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        ProtocolBuf.writeFloat(buf, 3.14159f);
        buf.flip();
        assertEquals(3.14159f, ProtocolBuf.readFloat(buf), 0.0001f);
    }
}
