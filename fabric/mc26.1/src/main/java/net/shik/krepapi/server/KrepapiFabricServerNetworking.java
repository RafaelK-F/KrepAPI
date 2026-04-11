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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.net.KrepapiInterceptKeysS2CPayload;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.net.KrepapiMouseActionC2SPayload;
import net.shik.krepapi.net.KrepapiMouseCaptureS2CPayload;
import net.shik.krepapi.net.KrepapiRawCaptureS2CPayload;
import net.shik.krepapi.net.KrepapiRawKeyC2SPayload;
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.KrepapiVersionRequirement;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiFabricServerNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("krepapi");

    @FunctionalInterface
    public interface KeyActionListener {
        void onKeyAction(ServerPlayer player, KrepapiKeyActionC2SPayload payload);
    }

    @FunctionalInterface
    public interface RawKeyListener {
        void onRawKey(ServerPlayer player, KrepapiRawKeyC2SPayload payload);
    }

    @FunctionalInterface
    public interface MouseActionListener {
        void onMouseAction(ServerPlayer player, KrepapiMouseActionC2SPayload payload);
    }

    public static final KrepapiFabricHandshakeState HANDSHAKE = new KrepapiFabricHandshakeState();

    private static final Map<UUID, Integer> HANDSHAKE_TICKS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<ModConstraint> MOD_CONSTRAINTS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<KeyActionListener> KEY_ACTION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<RawKeyListener> RAW_KEY_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<MouseActionListener> MOUSE_ACTION_LISTENERS = new CopyOnWriteArrayList<>();

    public static final KrepapiFabricServerSettings settings = new KrepapiFabricServerSettings();

    private KrepapiFabricServerNetworking() {
    }

    public static void registerMinimumBuildVersion(String modId, String semver) {
        KrepapiVersionRequirement.parse(semver.trim());
        MOD_CONSTRAINTS.add(new ModConstraint(modId, KrepapiVersionPolicy.Constraint.global(semver)));
    }

    public static void registerMinimumBuildVersionForFeature(String modId, String featureId, String semver) {
        KrepapiVersionRequirement.parse(semver.trim());
        MOD_CONSTRAINTS.add(new ModConstraint(modId, KrepapiVersionPolicy.Constraint.feature(featureId, semver)));
    }

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

    public static int getClientCapabilities(ServerPlayer player) {
        return HANDSHAKE.getClientCapabilities(player.getUUID());
    }

    private record ModConstraint(String modId, KrepapiVersionPolicy.Constraint constraint) {
    }

    public static void register() {
        try {
            KrepapiVersionRequirement.parse(settings.minimumModVersion.trim());
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Invalid KrepAPI minimumModVersion in KrepapiFabricServerSettings: {}", ex.getMessage());
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!settings.requireClientOnDedicatedServer || !server.isDedicatedServer()) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID id = player.getUUID();
                KrepapiFabricHandshakeState.Entry e = HANDSHAKE.get(id);
                if (e == null || e.answered) {
                    HANDSHAKE_TICKS.remove(id);
                    continue;
                }
                int t = HANDSHAKE_TICKS.merge(id, 1, Integer::sum);
                if (t >= settings.handshakeTimeoutTicks) {
                    HANDSHAKE_TICKS.remove(id);
                    HANDSHAKE.remove(id);
                    player.connection.disconnect(Component.literal(KrepapiKickReasons.HANDSHAKE_TIMEOUT));
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiClientInfoC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            KrepapiFabricHandshakeState.Entry entry =
                    HANDSHAKE.markAnswered(player.getUUID(), payload.challengeNonce());
            if (entry == null) {
                return;
            }
            if (payload.protocolVersion() != KrepapiProtocolVersion.CURRENT) {
                player.connection.disconnect(Component.literal(KrepapiKickReasons.PROTOCOL_MISMATCH));
                return;
            }
            KrepapiVersionPolicy.VersionCheckFailure fail = KrepapiVersionPolicy.firstVersionCheckFailure(
                    payload.modVersion(),
                    entry.configMin,
                    entry.constraintsSnapshot
            );
            if (fail != null) {
                player.connection.disconnect(Component.literal(KrepapiKickReasons.forVersionCheckFailure(fail)));
                return;
            }
            entry.clientCapabilities = payload.capabilities();
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiKeyActionC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            for (KeyActionListener listener : KEY_ACTION_LISTENERS) {
                try {
                    listener.onKeyAction(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI key action listener failed", t);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiRawKeyC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            for (RawKeyListener listener : RAW_KEY_LISTENERS) {
                try {
                    listener.onRawKey(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI raw key listener failed", t);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiMouseActionC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            for (MouseActionListener listener : MOUSE_ACTION_LISTENERS) {
                try {
                    listener.onMouseAction(player, payload);
                } catch (Throwable t) {
                    LOGGER.error("KrepAPI mouse action listener failed", t);
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            long nonce = server.overworld().getRandom().nextLong();
            byte flags = settings.requireClientOnDedicatedServer && server.isDedicatedServer()
                    ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                    : 0;
            String cfg = settings.minimumModVersion;
            List<KrepapiVersionPolicy.Constraint> snap = snapshotConstraints();
            try {
                KrepapiVersionPolicy.validateRequirements(cfg, snap);
            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid KrepAPI version requirements: {}", ex.getMessage());
                player.connection.disconnect(Component.literal("KrepAPI server version requirements are misconfigured."));
                return;
            }
            String effectiveMin = KrepapiVersionPolicy.effectiveMinimum(cfg, snap);
            HANDSHAKE.begin(player.getUUID(), nonce, effectiveMin, flags != 0, cfg, snap);
            HANDSHAKE_TICKS.put(player.getUUID(), 0);
            ServerPlayNetworking.send(player, new KrepapiHelloS2CPayload(
                    KrepapiProtocolVersion.CURRENT,
                    flags,
                    effectiveMin,
                    nonce
            ));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            HANDSHAKE.remove(uuid);
            HANDSHAKE_TICKS.remove(uuid);
        });
    }

    public static void sendBindings(ServerPlayer player, List<ProtocolMessages.BindingEntry> entries) {
        List<ProtocolMessages.BindingEntry> deduped = ProtocolMessages.dedupeBindingEntriesLastWins(entries);
        if (deduped.size() > ProtocolMessages.MAX_BINDING_ENTRIES) {
            throw new IllegalArgumentException("too many binding entries: " + deduped.size());
        }
        ArrayList<ProtocolMessages.BindingEntry> copy = new ArrayList<>(deduped);
        copy.sort(Comparator.comparing(ProtocolMessages.BindingEntry::actionId));
        ServerPlayNetworking.send(player, new KrepapiBindingsS2CPayload(copy));
    }

    public static void sendRawCaptureConfig(ServerPlayer player, ProtocolMessages.RawCaptureConfig config) {
        ServerPlayNetworking.send(player, new KrepapiRawCaptureS2CPayload(config));
    }

    public static void sendInterceptKeys(ServerPlayer player, ProtocolMessages.InterceptKeysSync sync) {
        if (sync.entries().size() > ProtocolMessages.MAX_INTERCEPT_ENTRIES) {
            throw new IllegalArgumentException("too many intercept entries: " + sync.entries().size());
        }
        ServerPlayNetworking.send(player, new KrepapiInterceptKeysS2CPayload(sync.entries()));
    }

    public static void sendMouseCaptureConfig(ServerPlayer player, ProtocolMessages.MouseCaptureConfig config) {
        if ((HANDSHAKE.getClientCapabilities(player.getUUID()) & KrepapiCapabilities.SERVER_MOUSE_CAPTURE) == 0) {
            return;
        }
        ServerPlayNetworking.send(player, new KrepapiMouseCaptureS2CPayload(config));
    }
}
