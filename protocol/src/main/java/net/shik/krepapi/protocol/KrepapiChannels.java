package net.shik.krepapi.protocol;

import java.util.Set;

/**
 * Plugin message / custom payload channel ids ({@code namespace:path}).
 */
public final class KrepapiChannels {
    public static final String NAMESPACE = "krepapi";

    public static final String S2C_HELLO = NAMESPACE + ":s2c_hello";
    public static final String C2S_CLIENT_INFO = NAMESPACE + ":c2s_client_info";
    public static final String S2C_BINDINGS = NAMESPACE + ":s2c_bindings";
    public static final String S2C_RAW_CAPTURE = NAMESPACE + ":s2c_raw_capture";
    public static final String S2C_INTERCEPT_KEYS = NAMESPACE + ":s2c_intercept_keys";
    public static final String C2S_KEY_ACTION = NAMESPACE + ":c2s_key_action";
    public static final String C2S_RAW_KEY = NAMESPACE + ":c2s_raw_key";
    public static final String S2C_MOUSE_CAPTURE = NAMESPACE + ":s2c_mouse_capture";
    public static final String C2S_MOUSE_ACTION = NAMESPACE + ":c2s_mouse_action";

    /**
     * Client-to-server play plugin channels defined by this protocol. Use with
     * {@link #isIncomingPlayChannel(String)} so message handlers ignore unknown ids.
     */
    public static final Set<String> INCOMING_PLAY_CHANNELS = Set.of(
            C2S_CLIENT_INFO,
            C2S_KEY_ACTION,
            C2S_RAW_KEY,
            C2S_MOUSE_ACTION
    );

    private KrepapiChannels() {
    }

    /** {@code true} if {@code channel} is a known C2S KrepAPI play channel. */
    public static boolean isIncomingPlayChannel(String channel) {
        return channel != null && INCOMING_PLAY_CHANNELS.contains(channel);
    }
}
