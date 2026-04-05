package net.shik.krepapi.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        clear(client);
        for (ProtocolMessages.BindingEntry e : entries) {
            String translationKey = "krepapi.server." + sanitize(e.actionId());
            KeyMapping mapping = KeyMappingCompat.createServerBinding(translationKey, e.defaultKey());
            KeyBindingHelper.registerKeyBinding(mapping);
            ACTIVE.put(e.actionId(), mapping);
        }
        KrepapiKeyPipeline.setServerOverrideBindings(entries);
    }

    public static void clear(Minecraft client) {
        KrepapiKeyPipeline.clearServerOverrides();
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
        ClientPlayNetworking.send(new KrepapiKeyActionC2SPayload(actionId, phase, seq));
    }

    private static String sanitize(String actionId) {
        return actionId.replaceAll("[^a-z0-9._-]", "_");
    }
}
