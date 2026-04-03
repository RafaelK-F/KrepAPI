package net.shik.krepapi.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Minecraft-compatible var-int and UTF on a {@link ByteBuffer} (Paper / tests).
 */
public final class ProtocolBuf {
    /** Maximum UTF-8 byte length for a single Minecraft-style string segment. */
    public static final int MAX_STRING = 32767;

    private ProtocolBuf() {
    }

    /** Varint encoding length for a 32-bit value (1–5 bytes), matching {@link #writeVarInt}. */
    public static int varIntEncodedSize(int value) {
        int n = 0;
        while (true) {
            n++;
            if ((value & ~0x7F) == 0) {
                return n;
            }
            value >>>= 7;
        }
    }

    /**
     * Byte length of a UTF-8 segment as written by {@link #writeUtf(ByteBuffer, String)} (varint length + payload).
     *
     * @throws IllegalArgumentException if UTF-8 exceeds {@link #MAX_STRING}
     */
    public static int utfEncodedSize(String s) {
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) {
            throw new IllegalArgumentException("string too long");
        }
        return varIntEncodedSize(bytes.length) + bytes.length;
    }

    /**
     * Like {@link #utfEncodedSize(String)} but enforces a tighter UTF-8 byte cap (e.g. action ids).
     *
     * @throws IllegalArgumentException if UTF-8 exceeds {@code maxUtf8Bytes} or {@link #MAX_STRING}
     */
    public static int utfEncodedSize(String s, int maxUtf8Bytes) {
        if (maxUtf8Bytes < 0 || maxUtf8Bytes > MAX_STRING) {
            throw new IllegalArgumentException("invalid maxUtf8Bytes");
        }
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxUtf8Bytes) {
            throw new IllegalArgumentException("string too long");
        }
        return varIntEncodedSize(bytes.length) + bytes.length;
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
        writeUtf(buf, s, MAX_STRING);
    }

    /**
     * Writes UTF-8 with a maximum encoded byte length of {@code maxUtf8Bytes} (inclusive).
     */
    public static void writeUtf(ByteBuffer buf, String s, int maxUtf8Bytes) {
        if (maxUtf8Bytes < 0 || maxUtf8Bytes > MAX_STRING) {
            throw new IllegalArgumentException("invalid maxUtf8Bytes");
        }
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxUtf8Bytes) {
            throw new IllegalArgumentException("string too long");
        }
        writeVarInt(buf, bytes.length);
        buf.put(bytes);
    }

    public static String readUtf(ByteBuffer buf) {
        return readUtf(buf, MAX_STRING);
    }

    /**
     * Reads UTF-8 with a maximum segment length of {@code maxUtf8Bytes} (inclusive).
     */
    public static String readUtf(ByteBuffer buf, int maxUtf8Bytes) {
        if (maxUtf8Bytes < 0 || maxUtf8Bytes > MAX_STRING) {
            throw new IllegalArgumentException("invalid maxUtf8Bytes");
        }
        int len = readVarInt(buf);
        if (len < 0 || len > maxUtf8Bytes || buf.remaining() < len) {
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
