package net.shik.krepapi.protocol;

/**
 * Wire handshake identity for KrepAPI 1.0+. Handshake payloads begin with a 5-byte prefix:
 * magic {@link #WIRE_MAGIC}, {@code schema}, {@code major}, {@code minor}, {@code patch}.
 * <p>
 * Legacy releases used a single leading varint (values 1–3) without this prefix; they are not interoperable with
 * current builds.
 */
public final class KrepapiProtocolVersion {

    /** First byte of {@link ProtocolMessages#encodeHello} / {@link ProtocolMessages#encodeClientInfo} payloads. */
    public static final byte WIRE_MAGIC = (byte) 0x4B;

    /** Schema generation 1: stable post-alpha wire (semver triple follows). */
    public static final int WIRE_SCHEMA_V1 = 1;

    /** Supported wire revision: schema 1, protocol semver 1.0.0. */
    public static final WireHandshakeHeader CURRENT_WIRE = new WireHandshakeHeader(WIRE_SCHEMA_V1, 1, 0, 0);

    private KrepapiProtocolVersion() {
    }

    /** True if the client/server wire header matches {@link #CURRENT_WIRE}. */
    public static boolean isCurrentWire(WireHandshakeHeader wire) {
        return wire != null
                && wire.schema() == CURRENT_WIRE.schema()
                && wire.major() == CURRENT_WIRE.major()
                && wire.minor() == CURRENT_WIRE.minor()
                && wire.patch() == CURRENT_WIRE.patch();
    }
}
