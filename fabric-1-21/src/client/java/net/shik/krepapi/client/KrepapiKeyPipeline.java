package net.shik.krepapi.client;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.shik.krepapi.api.KrepapiKeyEvent;
import net.shik.krepapi.api.KrepapiKeyListener;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Raw keyboard pipeline (GLFW key, scan code, action, modifiers). Runs before vanilla when mixin cancels.
 */
public final class KrepapiKeyPipeline {
    private static final List<RegisteredListener> LISTENERS = new CopyOnWriteArrayList<>();
    /** {@code actionId}s with {@code overrideVanilla}; matched live via {@link KeyMapping#matches(KeyEvent)}. */
    private static final List<String> SERVER_OVERRIDE_ACTION_IDS = new CopyOnWriteArrayList<>();
    /** {@code actionId}s currently held down for {@code overrideVanilla} repeat/release pairing (not raw key codes). */
    private static final Set<String> OVERRIDE_HELD_ACTION_IDS = new HashSet<>();

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
        SERVER_OVERRIDE_ACTION_IDS.clear();
        OVERRIDE_HELD_ACTION_IDS.clear();
        for (ProtocolMessages.BindingEntry e : entries) {
            if (e.overrideVanilla()) {
                SERVER_OVERRIDE_ACTION_IDS.add(e.actionId());
            }
        }
    }

    static void clearServerOverrides() {
        SERVER_OVERRIDE_ACTION_IDS.clear();
        OVERRIDE_HELD_ACTION_IDS.clear();
    }

    public static boolean dispatch(Minecraft client, KeyEvent input, int glfwAction) {
        int key = input.key();
        int scancode = input.scancode();
        int modifiers = input.modifiers();
        KrepapiKeyEvent event = new KrepapiKeyEvent(client, key, scancode, glfwAction, modifiers);
        boolean consume = false;
        for (RegisteredListener r : LISTENERS) {
            if (r.listener.onKey(event)) {
                consume = true;
                break;
            }
        }
        if (RawCaptureState.sendIfCapturing(client, key, scancode, glfwAction, modifiers)) {
            consume = true;
        }
        if (!consume && InterceptKeyState.shouldConsumeVanillaKey(key)) {
            consume = true;
        }
        if (!consume && shouldConsumeForServerBinding(input, glfwAction)) {
            consume = true;
        }
        return consume;
    }

    private static boolean shouldConsumeForServerBinding(KeyEvent input, int action) {
        if (action == GLFW.GLFW_PRESS) {
            boolean any = false;
            for (String actionId : SERVER_OVERRIDE_ACTION_IDS) {
                KeyMapping kb = ServerBindingManager.getKeyMapping(actionId);
                if (kb != null && kb.matches(input)) {
                    OVERRIDE_HELD_ACTION_IDS.add(actionId);
                    any = true;
                }
            }
            return any;
        }
        if (action == GLFW.GLFW_REPEAT) {
            for (String actionId : SERVER_OVERRIDE_ACTION_IDS) {
                KeyMapping kb = ServerBindingManager.getKeyMapping(actionId);
                if (kb != null && kb.matches(input) && OVERRIDE_HELD_ACTION_IDS.contains(actionId)) {
                    return true;
                }
            }
            return false;
        }
        if (action == GLFW.GLFW_RELEASE) {
            boolean any = false;
            for (String actionId : SERVER_OVERRIDE_ACTION_IDS) {
                KeyMapping kb = ServerBindingManager.getKeyMapping(actionId);
                if (kb != null && kb.matches(input) && OVERRIDE_HELD_ACTION_IDS.remove(actionId)) {
                    any = true;
                }
            }
            return any;
        }
        return false;
    }

    private record RegisteredListener(int priority, KrepapiKeyListener listener) {
    }
}
