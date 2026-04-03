package net.shik.krepapi.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KrepapiFabricHandshakeState {
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void begin(UUID playerId, long nonce, String minModVersion, boolean requireResponse) {
        entries.put(playerId, new Entry(nonce, minModVersion, requireResponse, false));
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
        public final String minModVersion;
        public final boolean requireResponse;
        public volatile boolean answered;

        Entry(long nonce, String minModVersion, boolean requireResponse, boolean answered) {
            this.nonce = nonce;
            this.minModVersion = minModVersion;
            this.requireResponse = requireResponse;
            this.answered = answered;
        }
    }
}
