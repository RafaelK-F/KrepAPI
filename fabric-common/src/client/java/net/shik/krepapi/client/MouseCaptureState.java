package net.shik.krepapi.client;

import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.net.KrepapiMouseActionC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Server-driven mouse capture ({@code s2c_mouse_capture} / {@code c2s_mouse_action}).
 */
public final class MouseCaptureState {
    private static volatile boolean enabled;
    private static volatile byte flags;
    private static volatile boolean consumeVanilla;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private MouseCaptureState() {
    }

    public static void apply(ProtocolMessages.MouseCaptureConfig config) {
        if (!config.enabled()) {
            clear();
            KrepapiDebugLog.mouseCaptureApplied(false, (byte) 0, false);
            return;
        }
        enabled = true;
        flags = config.flags();
        consumeVanilla = config.consumeVanilla();
        KrepapiDebugLog.mouseCaptureApplied(true, config.flags(), config.consumeVanilla());
    }

    public static void clear() {
        enabled = false;
        flags = 0;
        consumeVanilla = false;
    }

    public static boolean sendIfCapturingButton(Minecraft client, int button, int glfwAction, int modifiers) {
        if (client.getConnection() == null) {
            return false;
        }
        if (client.player == null && client.screen == null) {
            return false;
        }
        if (!enabled || (flags & ProtocolMessages.MOUSE_CAPTURE_BUTTONS) == 0) {
            return false;
        }
        byte extras = 0;
        float cx = 0f;
        float cy = 0f;
        if ((flags & ProtocolMessages.MOUSE_CAPTURE_CURSOR_ON_EVENTS) != 0) {
            extras |= ProtocolMessages.MOUSE_ACTION_EXTRA_HAS_CURSOR;
            int sw = client.getWindow().getGuiScaledWidth();
            int sh = client.getWindow().getGuiScaledHeight();
            if (sw > 0 && sh > 0) {
                cx = (float) (client.mouseHandler.xpos() / sw);
                cy = (float) (client.mouseHandler.ypos() / sh);
                cx = Math.min(1f, Math.max(0f, cx));
                cy = Math.min(1f, Math.max(0f, cy));
            }
        }
        int seq = SEQUENCE.incrementAndGet();
        KrepapiDebugLog.mouseActionSent("button", button, glfwAction, 0f, 0f, seq);
        var ev = new ProtocolMessages.MouseActionEvent(
                ProtocolMessages.MOUSE_ACTION_KIND_BUTTON,
                seq,
                (byte) button,
                (byte) glfwAction,
                modifiers,
                0f,
                0f,
                extras,
                cx,
                cy
        );
        ClientPlayNetworking.send(new KrepapiMouseActionC2SPayload(ev));
        return consumeVanilla;
    }

    public static boolean sendIfCapturingScroll(Minecraft client, double horizontal, double vertical) {
        if (client.getConnection() == null) {
            return false;
        }
        if (client.player == null && client.screen == null) {
            return false;
        }
        if (!enabled || (flags & ProtocolMessages.MOUSE_CAPTURE_SCROLL) == 0) {
            return false;
        }
        byte extras = 0;
        float cx = 0f;
        float cy = 0f;
        if ((flags & ProtocolMessages.MOUSE_CAPTURE_CURSOR_ON_EVENTS) != 0) {
            extras |= ProtocolMessages.MOUSE_ACTION_EXTRA_HAS_CURSOR;
            int sw = client.getWindow().getGuiScaledWidth();
            int sh = client.getWindow().getGuiScaledHeight();
            if (sw > 0 && sh > 0) {
                cx = (float) (client.mouseHandler.xpos() / sw);
                cy = (float) (client.mouseHandler.ypos() / sh);
                cx = Math.min(1f, Math.max(0f, cx));
                cy = Math.min(1f, Math.max(0f, cy));
            }
        }
        int seq = SEQUENCE.incrementAndGet();
        KrepapiDebugLog.mouseActionSent("scroll", 0, 0, (float) horizontal, (float) vertical, seq);
        var ev = new ProtocolMessages.MouseActionEvent(
                ProtocolMessages.MOUSE_ACTION_KIND_SCROLL,
                seq,
                (byte) 0,
                (byte) 0,
                0,
                (float) horizontal,
                (float) vertical,
                extras,
                cx,
                cy
        );
        ClientPlayNetworking.send(new KrepapiMouseActionC2SPayload(ev));
        return consumeVanilla;
    }
}
