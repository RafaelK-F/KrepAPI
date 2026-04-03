package net.shik.krepapi.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Registers {@link KeyBinding}s for the current server's binding list and sends {@link KrepapiKeyActionC2SPayload} on press.
 */
public final class ServerBindingManager {
    private static final Map<String, KeyBinding> ACTIVE = new HashMap<>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ServerBindingManager() {
    }

    public static void applyBindings(MinecraftClient client, List<ProtocolMessages.BindingEntry> entries) {
        clear(client);
        KrepapiKeyPipeline.setServerOverrideBindings(entries);
        for (ProtocolMessages.BindingEntry e : entries) {
            String translationKey = "krepapi.server." + sanitize(e.actionId());
            KeyBinding mapping = KeyBindingCompat.createServerBinding(translationKey, e.defaultKey());
            KeyBindingHelper.registerKeyBinding(mapping);
            ACTIVE.put(e.actionId(), mapping);
        }
    }

    public static void clear(MinecraftClient client) {
        KrepapiKeyPipeline.clearServerOverrides();
        ACTIVE.clear();
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        for (Map.Entry<String, KeyBinding> e : ACTIVE.entrySet()) {
            KeyBinding km = e.getValue();
            while (km.wasPressed()) {
                int seq = SEQUENCE.incrementAndGet();
                ClientPlayNetworking.send(new KrepapiKeyActionC2SPayload(
                        e.getKey(),
                        ProtocolMessages.KeyAction.PHASE_PRESS,
                        seq
                ));
            }
        }
    }

    private static String sanitize(String actionId) {
        return actionId.replaceAll("[^a-z0-9._-]", "_");
    }
}
