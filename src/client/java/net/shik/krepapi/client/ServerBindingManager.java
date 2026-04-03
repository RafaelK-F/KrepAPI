package net.shik.krepapi.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MinecraftClient;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Registers {@link KeyMapping}s for the current server's binding list and sends {@link KrepapiKeyActionC2SPayload} on press.
 */
public final class ServerBindingManager {
    private static final Map<String, KeyMapping> ACTIVE = new HashMap<>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ServerBindingManager() {
    }

    public static void applyBindings(MinecraftClient client, List<ProtocolMessages.BindingEntry> entries) {
        clear(client);
        KrepapiKeyPipeline.setServerOverrideBindings(entries);
        for (ProtocolMessages.BindingEntry e : entries) {
            String translationKey = "krepapi.server." + sanitize(e.actionId());
            KeyMapping mapping = new KeyMapping(
                    translationKey,
                    InputConstants.Type.KEYSYM,
                    e.defaultKey(),
                    "key.categories.krepapi.server"
            );
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
        for (Map.Entry<String, KeyMapping> e : ACTIVE.entrySet()) {
            KeyMapping km = e.getValue();
            while (km.consumeClick()) {
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
