package net.shik.krepapi.protocol;

/**
 * Bitfield sent in {@link ProtocolMessages.ClientInfo} from the client.
 */
public final class KrepapiCapabilities {
    /** Client can honor {@code overrideVanilla} on server bindings. */
    public static final int KEY_OVERRIDE = 1 << 0;
    /** Client exposes raw key events to mods (KrepAPI pipeline). */
    public static final int RAW_KEYS = 1 << 1;

    private KrepapiCapabilities() {
    }
}
