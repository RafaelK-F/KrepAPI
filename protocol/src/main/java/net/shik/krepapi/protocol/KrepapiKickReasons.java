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

    /**
     * Disconnect message when the client's build version is below the server's required minimum (SemVer).
     */
    public static String modBuildVersionTooOld(String requiredMinimum) {
        return "KrepAPI client mod is too old. Required build version " + requiredMinimum + " or newer.";
    }

    /**
     * Same as {@link #modBuildVersionTooOld(String)} but names a server-side feature / constraint label.
     */
    public static String modBuildVersionTooOldForFeature(String featureId, String requiredMinimum) {
        return "KrepAPI client mod is too old for \"" + featureId + "\". Required build version "
                + requiredMinimum + " or newer.";
    }
}
