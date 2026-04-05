package net.shik.krepapi.client;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Server-driven intercept of well-known non-{@code KeyMapping} keys ({@code s2c_intercept_keys}).
 */
public final class InterceptKeyState {
    private static final Map<Integer, Boolean> SLOT_TO_BLOCK = new HashMap<>();

    private InterceptKeyState() {
    }

    public static void apply(ProtocolMessages.InterceptKeysSync sync) {
        synchronized (SLOT_TO_BLOCK) {
            SLOT_TO_BLOCK.clear();
            for (ProtocolMessages.InterceptEntry e : sync.entries()) {
                int slot = e.slotId();
                if (slot >= ProtocolMessages.INTERCEPT_SLOT_ESCAPE && slot <= ProtocolMessages.INTERCEPT_SLOT_F5) {
                    SLOT_TO_BLOCK.put(slot, e.blockVanilla());
                }
            }
        }
    }

    public static void clear() {
        synchronized (SLOT_TO_BLOCK) {
            SLOT_TO_BLOCK.clear();
        }
    }

    public static boolean blockVanillaForSlot(int slotId) {
        synchronized (SLOT_TO_BLOCK) {
            return Boolean.TRUE.equals(SLOT_TO_BLOCK.get(slotId));
        }
    }

    /**
     * @return slot id in [{@link ProtocolMessages#INTERCEPT_SLOT_ESCAPE}, {@link ProtocolMessages#INTERCEPT_SLOT_F5}] or {@code -1}
     */
    public static int slotForGlfwKey(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return ProtocolMessages.INTERCEPT_SLOT_ESCAPE;
        }
        if (key == GLFW.GLFW_KEY_F3) {
            return ProtocolMessages.INTERCEPT_SLOT_F3;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            return ProtocolMessages.INTERCEPT_SLOT_TAB;
        }
        if (key == GLFW.GLFW_KEY_F1) {
            return ProtocolMessages.INTERCEPT_SLOT_F1;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            return ProtocolMessages.INTERCEPT_SLOT_F5;
        }
        return -1;
    }

    /**
     * Whether vanilla should not see this GLFW key event (press, repeat, or release).
     */
    public static boolean shouldConsumeVanillaKey(int glfwKey) {
        int slot = slotForGlfwKey(glfwKey);
        if (slot < 0) {
            return false;
        }
        return blockVanillaForSlot(slot);
    }
}
