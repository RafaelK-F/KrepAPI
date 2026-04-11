package net.shik.krepapi.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Registers {@link KeyMapping}s for the current server's binding list and sends {@link KrepapiKeyActionC2SPayload}
 * on press and release (via {@link KeyMapping#isDown()} edge detection).
 */
public final class ServerBindingManager {
    private static final Map<String, KeyMapping> ACTIVE = new HashMap<>();
    /** Previous-tick {@link KeyMapping#isDown()} per {@code actionId}; cleared with {@link #clear}. */
    private static final Map<String, Boolean> PREV_HELD = new HashMap<>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ServerBindingManager() {
    }

    /** For {@link KrepapiKeyPipeline} override-vanilla matching against the live bound key. */
    static KeyMapping getKeyMapping(String actionId) {
        return ACTIVE.get(actionId);
    }

    public static void applyBindings(Minecraft client, List<ProtocolMessages.BindingEntry> entries) {
        List<ProtocolMessages.BindingEntry> unique = ProtocolMessages.dedupeBindingEntriesLastWins(entries);
        clear(client);
        ServerBindingLabels.apply(client, unique);
        for (ProtocolMessages.BindingEntry e : unique) {
            String storageKey = bindingStorageTranslationKey(e.actionId());
            KeyMapping mapping = KeyMappingCompat.createServerBinding(storageKey, e.defaultKey(), e);
            KeyBindingHelper.registerKeyBinding(mapping);
            ACTIVE.put(e.actionId(), mapping);
        }
        KrepapiKeyPipeline.setServerOverrideBindings(unique);
        KrepapiDebugLog.bindingsApplied(unique.stream().map(ProtocolMessages.BindingEntry::actionId).toList());
    }

    public static void clear(Minecraft client) {
        KrepapiKeyPipeline.clearServerOverrides();
        if (client != null) {
            unregisterServerKeyMappings(client, ACTIVE.values());
        }
        ServerBindingLabels.clear(client);
        ACTIVE.clear();
        PREV_HELD.clear();
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        for (Map.Entry<String, KeyMapping> e : ACTIVE.entrySet()) {
            String actionId = e.getKey();
            KeyMapping km = e.getValue();
            boolean down = km.isDown();
            boolean prev = Boolean.TRUE.equals(PREV_HELD.get(actionId));
            if (!prev && down) {
                sendKeyAction(actionId, ProtocolMessages.KeyAction.PHASE_PRESS);
            } else if (prev && !down) {
                sendKeyAction(actionId, ProtocolMessages.KeyAction.PHASE_RELEASE);
            }
            PREV_HELD.put(actionId, down);
        }
    }

    private static void sendKeyAction(String actionId, byte phase) {
        int seq = SEQUENCE.incrementAndGet();
        KrepapiDebugLog.keyActionSent(actionId,
                phase == ProtocolMessages.KeyAction.PHASE_PRESS ? "press" : "release", seq);
        ClientPlayNetworking.send(new KrepapiKeyActionC2SPayload(actionId, phase, seq));
    }

    private static String sanitize(String actionId) {
        return actionId.replaceAll("[^a-z0-9._-]", "_");
    }

    /** Stable {@link KeyMapping} id / translation key: {@code krepapi.server.<sanitized actionId>}. */
    static String bindingStorageTranslationKey(String actionId) {
        return "krepapi.server." + sanitize(actionId);
    }

    /**
     * Drops server {@link KeyMapping}s from the controls list and from the Fabric modded registry so reconnect /
     * rebinding cannot leave duplicates or stale instances behind. Fabric exposes no public unregister API.
     */
    private static void unregisterServerKeyMappings(Minecraft client, Collection<KeyMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        Set<KeyMapping> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        for (KeyMapping km : mappings) {
            if (km != null) {
                toRemove.add(km);
            }
        }
        if (toRemove.isEmpty()) {
            return;
        }
        if (client.options != null) {
            KeyMapping[] keys = client.options.keyMappings;
            List<KeyMapping> kept = new ArrayList<>(keys.length);
            for (KeyMapping k : keys) {
                if (!toRemove.contains(k)) {
                    kept.add(k);
                }
            }
            if (kept.size() != keys.length) {
                client.options.keyMappings = kept.toArray(new KeyMapping[0]);
            }
        }
        removeFromFabricModdedRegistry(toRemove);
    }

    @SuppressWarnings("unchecked")
    private static void removeFromFabricModdedRegistry(Set<KeyMapping> toRemove) {
        String[] implClasses = {
                "net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl",
                "net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl",
        };
        for (String className : implClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                Field field = clazz.getDeclaredField("MODDED_KEY_BINDINGS");
                field.setAccessible(true);
                List<KeyMapping> modded = (List<KeyMapping>) field.get(null);
                for (Iterator<KeyMapping> it = modded.iterator(); it.hasNext(); ) {
                    if (toRemove.contains(it.next())) {
                        it.remove();
                    }
                }
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                // Wrong Fabric module layout for this game version; try the other impl name.
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // Best-effort: options array was still sanitized above.
            }
        }
    }
}
