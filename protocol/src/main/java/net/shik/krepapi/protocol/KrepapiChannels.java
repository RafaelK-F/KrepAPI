package net.shik.krepapi.protocol;

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

    private KrepapiChannels() {
    }
}
