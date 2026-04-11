package net.shik.krepapi.protocol;

/**
 * Shared server-side debug activation for Fabric dedicated servers and Paper plugins, aligned with the
 * Fabric client marker in {@code config/krepapi/krepapi-debug.txt} and JVM flag {@code -Dkrepapi.debug=true}.
 */
public final class KrepapiServerDebug {

    /** JVM system property; value {@code true} (case-insensitive) enables debug. */
    public static final String JVM_PROPERTY = "krepapi.debug";

    /**
     * Empty marker file in the JVM working directory (server root for Paper / dedicated server for Fabric)
     * enables debug, matching Fabric dedicated server networking.
     */
    public static final String MARKER_FILENAME = "krepapi-debug.txt";

    private KrepapiServerDebug() {
    }

    /**
     * {@code true} when {@code -Dkrepapi.debug=true} or a file named {@value #MARKER_FILENAME} exists in the
     * current working directory.
     */
    public static boolean jvmOrMarkerFileEnabled() {
        if ("true".equalsIgnoreCase(System.getProperty(JVM_PROPERTY))) {
            return true;
        }
        return new java.io.File(MARKER_FILENAME).exists();
    }

    /** JSON string literal content for embedding in NDJSON (escapes backslash and double quote). */
    public static String jsonString(String v) {
        if (v == null) {
            return "null";
        }
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
