package net.shik.krepapi.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Minecraft-compatible var-int and UTF on a {@link ByteBuffer} (Paper / tests).
 */
public final class ProtocolBuf {
    private static final int MAX_STRING = 32767;

    private ProtocolBuf() {
    }

    public static void writeVarInt(ByteBuffer buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.put((byte) value);
                return;
            }
            buf.put((byte) (value & 0x7F | 0x80));
            value >>>= 7;
        }
    }

    public static int readVarInt(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            if (!buf.hasRemaining()) {
                throw new IllegalArgumentException("truncated varint");
            }
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IllegalArgumentException("varint too big");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    public static void writeUtf(ByteBuffer buf, String s) {
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) {
            throw new IllegalArgumentException("string too long");
        }
        writeVarInt(buf, bytes.length);
        buf.put(bytes);
    }

    public static String readUtf(ByteBuffer buf) {
        int len = readVarInt(buf);
        if (len < 0 || len > MAX_STRING || buf.remaining() < len) {
            throw new IllegalArgumentException("invalid utf length");
        }
        byte[] tmp = new byte[len];
        buf.get(tmp);
        return new String(tmp, StandardCharsets.UTF_8);
    }

    public static void writeByte(ByteBuffer buf, int b) {
        buf.put((byte) b);
    }

    public static int readUnsignedByte(ByteBuffer buf) {
        return buf.get() & 0xFF;
    }

    public static void writeLong(ByteBuffer buf, long v) {
        buf.putLong(v);
    }

    public static long readLong(ByteBuffer buf) {
        return buf.getLong();
    }
}
