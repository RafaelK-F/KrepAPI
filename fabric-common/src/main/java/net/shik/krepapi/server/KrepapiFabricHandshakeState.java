package net.shik.krepapi.server;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.shik.krepapi.protocol.KrepapiVersionPolicy;

public final class KrepapiFabricHandshakeState {
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void begin(
            UUID playerId,
            long nonce,
            String effectiveMin,
            boolean requireResponse,
            String configMin,
            List<KrepapiVersionPolicy.Constraint> constraintsSnapshot
    ) {
        entries.put(playerId, new Entry(nonce, effectiveMin, requireResponse, false, configMin, constraintsSnapshot));
    }

    /**
     * If an entry exists for {@code playerId} with matching {@code nonce} and is not yet answered,
     * marks it answered atomically and returns it. Otherwise returns {@code null}.
     * <p>
     * Uses {@link ConcurrentHashMap#compute} so a concurrent {@link #remove} cannot interleave
     * between marking answered and obtaining the entry reference (which would leave capabilities unset).
     */
    public Entry markAnswered(UUID playerId, long nonce) {
        final Entry[] slot = new Entry[1];
        entries.compute(playerId, (id, e) -> {
            if (e == null || e.nonce != nonce || e.answered) {
                return e;
            }
            e.answered = true;
            slot[0] = e;
            return e;
        });
        return slot[0];
    }

    public Entry get(UUID playerId) {
        return entries.get(playerId);
    }

    public void remove(UUID playerId) {
        entries.remove(playerId);
    }

    /**
     * Stores {@link net.shik.krepapi.protocol.ProtocolMessages.ClientInfo#capabilities()} after a successful handshake.
     */
    public void setClientCapabilities(UUID playerId, int capabilities) {
        Entry e = entries.get(playerId);
        if (e != null) {
            e.clientCapabilities = capabilities;
        }
    }

    public int getClientCapabilities(UUID playerId) {
        Entry e = entries.get(playerId);
        return e == null ? 0 : e.clientCapabilities;
    }

    public static final class Entry {
        public final long nonce;
        public final String effectiveMin;
        public final boolean requireResponse;
        public final String configMin;
        public final List<KrepapiVersionPolicy.Constraint> constraintsSnapshot;
        public volatile boolean answered;
        /** Bitfield from {@code c2s_client_info}; {@code 0} until handshake completes successfully. */
        public volatile int clientCapabilities;

        Entry(
                long nonce,
                String effectiveMin,
                boolean requireResponse,
                boolean answered,
                String configMin,
                List<KrepapiVersionPolicy.Constraint> constraintsSnapshot
        ) {
            this.nonce = nonce;
            this.effectiveMin = effectiveMin;
            this.requireResponse = requireResponse;
            this.answered = answered;
            this.configMin = configMin;
            this.constraintsSnapshot = constraintsSnapshot;
        }
    }
}
