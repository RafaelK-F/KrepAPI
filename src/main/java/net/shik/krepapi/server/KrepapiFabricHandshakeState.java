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

    public boolean markAnswered(UUID playerId, long nonce) {
        Entry e = entries.get(playerId);
        if (e == null || e.nonce != nonce) {
            return false;
        }
        e.answered = true;
        return true;
    }

    public Entry get(UUID playerId) {
        return entries.get(playerId);
    }

    public void remove(UUID playerId) {
        entries.remove(playerId);
    }

    public static final class Entry {
        public final long nonce;
        public final String effectiveMin;
        public final boolean requireResponse;
        public final String configMin;
        public final List<KrepapiVersionPolicy.Constraint> constraintsSnapshot;
        public volatile boolean answered;

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
