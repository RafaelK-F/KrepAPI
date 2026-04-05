package net.shik.krepapi.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.net.KrepapiInterceptKeysS2CPayload;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.net.KrepapiMouseActionC2SPayload;
import net.shik.krepapi.net.KrepapiMouseCaptureS2CPayload;
import net.shik.krepapi.net.KrepapiRawCaptureS2CPayload;
import net.shik.krepapi.net.KrepapiRawKeyC2SPayload;
import net.shik.krepapi.protocol.KrepapiBuildVersion;
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiFabricServerNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("krepapi");

    /**
     * Receives {@link KrepapiKeyActionC2SPayload} on the dedicated server when mods register a listener.
     * The built-in mod does not implement gameplay handling; Paper users get logging from KrepAPI-Paper instead.
     */
    @FunctionalInterface
    public interface KeyActionListener {
        void onKeyAction(ServerPlayerEntity player, KrepapiKeyActionC2SPayload payload);
    }

    /**
     * Receives {@link KrepapiRawKeyC2SPayload} when mods register a listener.
     */
    @FunctionalInterface
    public interface RawKeyListener {
        void onRawKey(ServerPlayerEntity player, KrepapiRawKeyC2SPayload payload);
    }

    /**
     * Receives {@link KrepapiMouseActionC2SPayload} when mods register a listener.
     */
    @FunctionalInterface
    public interface MouseActionListener {
        void onMouseAction(ServerPlayerEntity player, KrepapiMouseActionC2SPayload payload);
    }

    public static final KrepapiFabricHandshakeState HANDSHAKE = new KrepapiFabricHandshakeState();

    /**
     * Per-player tick counter while waiting for {@code c2s_client_info}. Cleared when the player answers,
     * on handshake timeout (together with {@link #HANDSHAKE}), and on {@link ServerPlayConnectionEvents#DISCONNECT}.
     */
    private static final Map<UUID, Integer> HANDSHAKE_TICKS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<ModConstraint> MOD_CONSTRAINTS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<KeyActionListener> KEY_ACTION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<RawKeyListener> RAW_KEY_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<MouseActionListener> MOUSE_ACTION_LISTENERS = new CopyOnWriteArrayList<>();

    /** Handshake and version-gate options for the dedicated Fabric server. */
    public static final KrepapiFabricServerSettings settings = new KrepapiFabricServerSettings();

    private KrepapiFabricServerNetworking() {
    }

    /**
     * Registers a global minimum KrepAPI client build version for {@code modId} (e.g. your mod's Fabric id).
     */
    public static void registerMinimumBuildVersion(String modId, String semver) {
        MOD_CONSTRAINTS.add(new ModConstraint(modId, KrepapiVersionPolicy.Constraint.global(semver)));
    }

    /**
     * Registers a named feature constraint for {@code modId}.
     */
    public static void registerMinimumBuildVersionForFeature(String modId, String featureId, String semver) {
        MOD_CONSTRAINTS.add(new ModConstraint(modId, KrepapiVersionPolicy.Constraint.feature(featureId, semver)));
    }

    /**
     * Removes all build-version requirements registered under {@code modId}.
     */
    public static void clearBuildRequirementsForMod(String modId) {
        MOD_CONSTRAINTS.removeIf(m -> modId.equals(m.modId));
    }

    private static List<KrepapiVersionPolicy.Constraint> snapshotConstraints() {
        return MOD_CONSTRAINTS.stream().map(ModConstraint::constraint).toList();
    }

    public static void registerKeyActionListener(KeyActionListener listener) {
        KEY_ACTION_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void unregisterKeyActionListener(KeyActionListener listener) {
        KEY_ACTION_LISTENERS.remove(listener);
    }

    public static void registerRawKeyListener(RawKeyListener listener) {
        RAW_KEY_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void unregisterRawKeyListener(RawKeyListener listener) {
        RAW_KEY_LISTENERS.remove(listener);
    }

    public static void registerMouseActionListener(MouseActionListener listener) {
        MOUSE_ACTION_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void unregisterMouseActionListener(MouseActionListener listener) {
        MOUSE_ACTION_LISTENERS.remove(listener);
    }

    /**
     * Capability bitfield from the player's last successful {@code c2s_client_info}, or {@code 0} if unknown.
     */
    public static int getClientCapabilities(ServerPlayerEntity player) {
        return HANDSHAKE.getClientCapabilities(player.getUuid());
    }

    private record ModConstraint(String modId, KrepapiVersionPolicy.Constraint constraint) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!settings.requireClientOnDedicatedServer || !server.isDedicated()) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                KrepapiFabricHandshakeState.Entry e = HANDSHAKE.get(id);
                if (e == null || e.answered) {
                    HANDSHAKE_TICKS.remove(id);
                    continue;
                }
                int t = HANDSHAKE_TICKS.merge(id, 1, Integer::sum);
                if (t >= settings.handshakeTimeoutTicks) {
                    HANDSHAKE_TICKS.remove(id);
                    HANDSHAKE.remove(id);
                    player.networkHandler.disconnect(Text.literal(KrepapiKickReasons.HANDSHAKE_TIMEOUT));
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiClientInfoC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!HANDSHAKE.markAnswered(player.getUuid(), payload.challengeNonce())) {
                return;
            }
            if (payload.protocolVersion() != KrepapiProtocolVersion.CURRENT) {
                player.networkHandler.disconnect(Text.literal(KrepapiKickReasons.PROTOCOL_MISMATCH));
                return;
            }
            KrepapiFabricHandshakeState.Entry entry = HANDSHAKE.get(player.getUuid());
            if (entry == null) {
                return;
            }
            if (!KrepapiBuildVersion.isAtLeast(payload.modVersion(), entry.effectiveMin)) {
                KrepapiVersionPolicy.Constraint fail = KrepapiVersionPolicy.strictestFailure(
                        payload.modVersion(),
                        entry.configMin,
                        entry.constraintsSnapshot
                );
                String kick;
                if (fail != null && fail.featureId() != null) {
                    kick = KrepapiKickReasons.modBuildVersionTooOldForFeature(fail.featureId(), fail.minimumBuildVersion());
                } else if (fail != null) {
                    kick = KrepapiKickReasons.modBuildVersionTooOld(fail.minimumBuildVersion());
                } else {
                    kick = KrepapiKickReasons.modBuildVersionTooOld(entry.effectiveMin);
                }
                player.networkHandler.disconnect(Text.literal(kick));
                return;
            }
            HANDSHAKE.setClientCapabilities(player.getUuid(), payload.capabilities());
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiKeyActionC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            for (KeyActionListener listener : KEY_ACTION_LISTENERS) {
                try {
                    listener.onKeyAction(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI key action listener failed", t);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiRawKeyC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            for (RawKeyListener listener : RAW_KEY_LISTENERS) {
                try {
                    listener.onRawKey(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI raw key listener failed", t);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiMouseActionC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            for (MouseActionListener listener : MOUSE_ACTION_LISTENERS) {
                try {
                    listener.onMouseAction(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI mouse action listener failed", t);
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            long nonce = server.getOverworld().getRandom().nextLong();
            byte flags = settings.requireClientOnDedicatedServer && server.isDedicated()
                    ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                    : 0;
            String cfg = settings.minimumModVersion;
            List<KrepapiVersionPolicy.Constraint> snap = snapshotConstraints();
            String effectiveMin = KrepapiVersionPolicy.effectiveMinimum(cfg, snap);
            HANDSHAKE.begin(player.getUuid(), nonce, effectiveMin, flags != 0, cfg, snap);
            HANDSHAKE_TICKS.put(player.getUuid(), 0);
            ServerPlayNetworking.send(player, new KrepapiHelloS2CPayload(
                    KrepapiProtocolVersion.CURRENT,
                    flags,
                    effectiveMin,
                    nonce
            ));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            HANDSHAKE.remove(uuid);
            HANDSHAKE_TICKS.remove(uuid);
        });
    }

    public static void sendBindings(ServerPlayerEntity player, List<ProtocolMessages.BindingEntry> entries) {
        if (entries.size() > ProtocolMessages.MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("too many binding entries: " + entries.size());
        }
        ArrayList<ProtocolMessages.BindingEntry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparing(ProtocolMessages.BindingEntry::actionId));
        ServerPlayNetworking.send(player, new KrepapiBindingsS2CPayload(copy));
    }

    public static void sendRawCaptureConfig(ServerPlayerEntity player, ProtocolMessages.RawCaptureConfig config) {
        ServerPlayNetworking.send(player, new KrepapiRawCaptureS2CPayload(config));
    }

    public static void sendInterceptKeys(ServerPlayerEntity player, ProtocolMessages.InterceptKeysSync sync) {
        if (sync.entries().size() > ProtocolMessages.MAX_INTERCEPT_ENTRIES) {
            throw new IllegalArgumentException("too many intercept entries: " + sync.entries().size());
        }
        ServerPlayNetworking.send(player, new KrepapiInterceptKeysS2CPayload(sync.entries()));
    }

    /**
     * Sends {@code s2c_mouse_capture} only if the client advertised {@link KrepapiCapabilities#SERVER_MOUSE_CAPTURE}.
     */
    public static void sendMouseCaptureConfig(ServerPlayerEntity player, ProtocolMessages.MouseCaptureConfig config) {
        if ((HANDSHAKE.getClientCapabilities(player.getUuid()) & KrepapiCapabilities.SERVER_MOUSE_CAPTURE) == 0) {
            return;
        }
        ServerPlayNetworking.send(player, new KrepapiMouseCaptureS2CPayload(config));
    }
}
