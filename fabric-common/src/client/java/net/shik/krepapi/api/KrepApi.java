package net.shik.krepapi.api;

import net.shik.krepapi.client.KrepapiKeyPipeline;

/**
 * Stable entry points for companion mods integrating with KrepAPI on the client.
 */
public final class KrepApi {
    private KrepApi() {
    }

    /**
     * Register a raw key listener. Higher {@code priority} runs first (see {@link #registerRawKeyListener(int, KrepapiKeyListener)}).
     */
    public static void registerRawKeyListener(KrepapiKeyListener listener) {
        KrepapiKeyPipeline.register(listener);
    }

    public static void registerRawKeyListener(int priority, KrepapiKeyListener listener) {
        KrepapiKeyPipeline.register(priority, listener);
    }

    public static void unregisterRawKeyListener(KrepapiKeyListener listener) {
        KrepapiKeyPipeline.unregister(listener);
    }
}
