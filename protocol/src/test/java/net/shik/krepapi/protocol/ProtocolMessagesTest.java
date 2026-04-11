package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProtocolMessagesTest {

    @Test
    void helloRoundTripWithMaxLengthMinModVersion() {
        String longVer = "x".repeat(ProtocolBuf.MAX_STRING);
        ProtocolMessages.Hello msg = new ProtocolMessages.Hello(1, (byte) 0, longVer, 42L);
        byte[] enc = ProtocolMessages.encodeHello(msg);
        ProtocolMessages.Hello dec = ProtocolMessages.decodeHello(enc);
        assertEquals(longVer, dec.minModVersion());
        assertEquals(42L, dec.challengeNonce());
    }

    @Test
    void clientInfoRoundTripWithMaxLengthModVersion() {
        String longVer = "y".repeat(ProtocolBuf.MAX_STRING);
        ProtocolMessages.ClientInfo msg = new ProtocolMessages.ClientInfo(1, longVer, 3, -1L);
        byte[] enc = ProtocolMessages.encodeClientInfo(msg);
        ProtocolMessages.ClientInfo dec = ProtocolMessages.decodeClientInfo(enc);
        assertEquals(longVer, dec.modVersion());
    }

    @Test
    void decodeBindingsRejectsExcessiveCount() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        ProtocolBuf.writeVarInt(buf, ProtocolMessages.MAX_BINDING_ENTRIES + 1);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.decodeBindingsSync(data));
    }

    @Test
    void encodeBindingsRejectsTooManyEntries() {
        List<ProtocolMessages.BindingEntry> entries = new ArrayList<>();
        for (int i = 0; i < ProtocolMessages.MAX_BINDING_ENTRIES + 1; i++) {
            entries.add(new ProtocolMessages.BindingEntry("a", "b", 0, false, "c"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolMessages.encodeBindingsSync(new ProtocolMessages.BindingsSync(entries))
        );
    }

    @Test
    void dedupeBindingEntriesLastWinsKeepsLastPerActionIdInScanOrder() {
        ProtocolMessages.BindingEntry x0 = new ProtocolMessages.BindingEntry("x", "first", 1, false, "c");
        ProtocolMessages.BindingEntry y = new ProtocolMessages.BindingEntry("y", "y", 2, false, "c");
        ProtocolMessages.BindingEntry x1 = new ProtocolMessages.BindingEntry("x", "second", 3, true, "d");
        List<ProtocolMessages.BindingEntry> in = List.of(x0, y, x1);
        List<ProtocolMessages.BindingEntry> out = ProtocolMessages.dedupeBindingEntriesLastWins(in);
        assertEquals(2, out.size());
        assertEquals(y, out.get(0));
        assertEquals(x1, out.get(1));
        assertEquals("second", out.get(1).displayName());
        assertEquals(3, out.get(1).defaultKey());
        assertEquals(true, out.get(1).overrideVanilla());
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
    void bindingsRoundTripAtMaxEntryCount() {
        List<ProtocolMessages.BindingEntry> entries = new ArrayList<>();
        for (int i = 0; i < ProtocolMessages.MAX_BINDING_ENTRIES; i++) {
            entries.add(new ProtocolMessages.BindingEntry(
                    "id" + i,
                    "name",
                    i,
                    false,
                    "cat"
            ));
        }
        byte[] enc = ProtocolMessages.encodeBindingsSync(new ProtocolMessages.BindingsSync(entries));
        ProtocolMessages.BindingsSync dec = ProtocolMessages.decodeBindingsSync(enc);
        assertEquals(ProtocolMessages.MAX_BINDING_ENTRIES, dec.entries().size());
        assertEquals("id0", dec.entries().getFirst().actionId());
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
