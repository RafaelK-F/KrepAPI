package net.shik.krepapi.server;

/**
 * Mutable Fabric dedicated-server handshake options. Use {@link KrepapiFabricServerNetworking#settings}.
 */
public final class KrepapiFabricServerSettings {

    /** If true on a dedicated server, players without a valid handshake are kicked. */
    public volatile boolean requireClientOnDedicatedServer = false;

    /** Config-style floor; combined with {@link KrepapiFabricServerNetworking#registerMinimumBuildVersion} / feature registrations. */
    public volatile String minimumModVersion = "1.3.0";

    public volatile int handshakeTimeoutTicks = 200;

    KrepapiFabricServerSettings() {
    }
}
