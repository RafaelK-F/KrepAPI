package net.shik.krepapi.client;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shik.krepapi.net.KrepapiRawKeyC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Server-driven raw keyboard capture ({@code s2c_raw_capture} / {@code c2s_raw_key}).
 */
public final class RawCaptureState {
    private static volatile boolean enabled;
    private static volatile byte mode = ProtocolMessages.RAW_CAPTURE_MODE_OFF;
    private static volatile boolean consumeVanilla;
    private static final Set<Integer> WHITELIST = new HashSet<>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private RawCaptureState() {
    }

    public static void apply(ProtocolMessages.RawCaptureConfig config) {
        if (!config.enabled() || config.mode() == ProtocolMessages.RAW_CAPTURE_MODE_OFF) {
            clear();
            return;
        }
        synchronized (WHITELIST) {
            enabled = true;
            mode = config.mode();
            consumeVanilla = config.consumeVanilla();
            WHITELIST.clear();
            if (config.mode() == ProtocolMessages.RAW_CAPTURE_MODE_WHITELIST) {
                WHITELIST.addAll(config.whitelistKeys());
            }
        }
    }

    public static void clear() {
        synchronized (WHITELIST) {
            enabled = false;
            mode = ProtocolMessages.RAW_CAPTURE_MODE_OFF;
            consumeVanilla = false;
            WHITELIST.clear();
        }
    }

    /**
     * If raw capture applies to this key, sends {@link KrepapiRawKeyC2SPayload} and returns whether
     * {@link ProtocolMessages.RawCaptureConfig#consumeVanilla()} should suppress vanilla handling.
     */
    public static boolean sendIfCapturing(MinecraftClient client, int key, int scancode, int glfwAction, int modifiers) {
        if (client.getNetworkHandler() == null) {
            return false;
        }
        if (client.player == null && client.currentScreen == null) {
            return false;
        }
        boolean active;
        boolean whitelistMode;
        boolean cv;
        synchronized (WHITELIST) {
            active = enabled;
            whitelistMode = mode == ProtocolMessages.RAW_CAPTURE_MODE_WHITELIST;
            cv = consumeVanilla;
            if (!active) {
                return false;
            }
            if (whitelistMode && !WHITELIST.contains(key)) {
                return false;
            }
        }
        int seq = SEQUENCE.incrementAndGet();
        ClientPlayNetworking.send(new KrepapiRawKeyC2SPayload(key, scancode, (byte) glfwAction, modifiers, seq));
        return cv;
    }
}
