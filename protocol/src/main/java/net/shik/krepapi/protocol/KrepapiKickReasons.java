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

    /** Client is above a {@code <X.Y.Z} (exclusive) ceiling. */
    public static String modBuildVersionTooNew(String requirementSpec) {
        return "KrepAPI client mod build is too new for this server (must satisfy " + requirementSpec + ").";
    }

    public static String modBuildVersionTooNewForFeature(String featureId, String requirementSpec) {
        return "KrepAPI client mod build is too new for \"" + featureId + "\" (must satisfy " + requirementSpec + ").";
    }

    /** Client is not on the required major.minor line ({@code X.Y}, {@code X.Y.x}). */
    public static String modBuildVersionWrongFamily(String requirementSpec) {
        return "KrepAPI client mod build must satisfy " + requirementSpec + " (major.minor line).";
    }

    public static String modBuildVersionWrongFamilyForFeature(String featureId, String requirementSpec) {
        return "KrepAPI client mod build must satisfy " + requirementSpec + " for \"" + featureId + "\".";
    }

    /** Client build does not match {@code =X.Y.Z}. */
    public static String modBuildVersionExactMismatch(String requirementSpec) {
        return "KrepAPI client mod build must satisfy " + requirementSpec + " (exact build).";
    }

    public static String modBuildVersionExactMismatchForFeature(String featureId, String requirementSpec) {
        return "KrepAPI client mod build must satisfy " + requirementSpec + " for \"" + featureId + "\" (exact build).";
    }

    /** Disconnect message for the first failed build-version requirement from {@link KrepapiVersionPolicy#firstVersionCheckFailure}. */
    public static String forVersionCheckFailure(KrepapiVersionPolicy.VersionCheckFailure f) {
        KrepapiVersionPolicy.Constraint c = f.constraint();
        String spec = c.minimumBuildVersion();
        String feature = c.featureId();
        return switch (f.reason()) {
            case TOO_LOW -> feature != null
                    ? modBuildVersionTooOldForFeature(feature, spec)
                    : modBuildVersionTooOld(spec);
            case TOO_HIGH -> feature != null
                    ? modBuildVersionTooNewForFeature(feature, spec)
                    : modBuildVersionTooNew(spec);
            case EXACT_MISMATCH -> feature != null
                    ? modBuildVersionExactMismatchForFeature(feature, spec)
                    : modBuildVersionExactMismatch(spec);
            case WRONG_FAMILY -> feature != null
                    ? modBuildVersionWrongFamilyForFeature(feature, spec)
                    : modBuildVersionWrongFamily(spec);
        };
    }
}
