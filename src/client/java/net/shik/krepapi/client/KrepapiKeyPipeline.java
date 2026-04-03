package net.shik.krepapi.client;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.MinecraftClient;
import net.shik.krepapi.api.KrepapiKeyEvent;
import net.shik.krepapi.api.KrepapiKeyListener;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Raw keyboard pipeline (GLFW key, scan code, action, modifiers). Runs before vanilla when mixin cancels.
 */
public final class KrepapiKeyPipeline {
    private static final List<RegisteredListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<ProtocolMessages.BindingEntry> SERVER_OVERRIDE_KEYS = new CopyOnWriteArrayList<>();

    private KrepapiKeyPipeline() {
    }

    public static void register(KrepapiKeyListener listener) {
        register(Integer.MAX_VALUE / 2, listener);
    }

    public static void register(int priority, KrepapiKeyListener listener) {
        LISTENERS.add(new RegisteredListener(priority, listener));
        LISTENERS.sort(Comparator.comparingInt(RegisteredListener::priority).reversed());
    }

    public static void unregister(KrepapiKeyListener listener) {
        LISTENERS.removeIf(r -> r.listener == listener);
    }

    static void setServerOverrideBindings(List<ProtocolMessages.BindingEntry> entries) {
        SERVER_OVERRIDE_KEYS.clear();
        for (ProtocolMessages.BindingEntry e : entries) {
            if (e.overrideVanilla()) {
                SERVER_OVERRIDE_KEYS.add(e);
            }
        }
    }

    static void clearServerOverrides() {
        SERVER_OVERRIDE_KEYS.clear();
    }

    /**
     * @return true if vanilla handling for this key event should be skipped
     */
    public static boolean dispatch(MinecraftClient client, int key, int scancode, int action, int modifiers) {
        KrepapiKeyEvent event = new KrepapiKeyEvent(client, key, scancode, action, modifiers);
        boolean consume = false;
        for (RegisteredListener r : LISTENERS) {
            if (r.listener.onKey(event)) {
                consume = true;
            }
        }
        if (!consume && shouldConsumeForServerBinding(client, key, action)) {
            consume = true;
        }
        return consume;
    }

    private static boolean shouldConsumeForServerBinding(MinecraftClient client, int key, int action) {
        if (action != 1) { // GLFW_PRESS
            return false;
        }
        for (ProtocolMessages.BindingEntry e : SERVER_OVERRIDE_KEYS) {
            if (e.defaultKey() == key) {
                return true;
            }
        }
        return false;
    }

    private record RegisteredListener(int priority, KrepapiKeyListener listener) {
    }
}
