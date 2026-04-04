package net.shik.krepapi.protocol;

/**
 * Bitfield sent in {@link ProtocolMessages.ClientInfo} from the client.
 */
public final class KrepapiCapabilities {
    /** Client can honor {@code overrideVanilla} on server bindings. */
    public static final int KEY_OVERRIDE = 1 << 0;
    /** Client exposes raw key events to mods (KrepAPI pipeline). */
    public static final int RAW_KEYS = 1 << 1;
    /** Client honors {@code s2c_raw_capture} and sends {@code c2s_raw_key}. */
    public static final int SERVER_RAW_CAPTURE = 1 << 2;
    /** Client honors {@code s2c_intercept_keys} (hardcoded-key mixins). */
    public static final int INTERCEPT_KEYS = 1 << 3;

    private KrepapiCapabilities() {
    }
}
