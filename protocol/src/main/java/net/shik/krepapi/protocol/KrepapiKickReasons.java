package net.shik.krepapi.protocol;

/**
 * Suggested disconnect / kick messages for server implementations.
 */
public final class KrepapiKickReasons {
    public static final String MISSING_CLIENT = "This server requires the KrepAPI client mod.";
    public static final String PROTOCOL_MISMATCH = "KrepAPI protocol version mismatch.";
    public static final String MOD_VERSION_TOO_OLD = "KrepAPI mod is too old for this server.";
    public static final String HANDSHAKE_TIMEOUT = "KrepAPI handshake timed out.";

    private KrepapiKickReasons() {
    }
}
