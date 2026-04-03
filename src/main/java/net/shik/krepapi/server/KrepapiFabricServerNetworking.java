package net.shik.krepapi.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.KrepapiBuildVersion;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiFabricServerNetworking {
    public static final KrepapiFabricHandshakeState HANDSHAKE = new KrepapiFabricHandshakeState();
    private static final Map<UUID, Integer> HANDSHAKE_TICKS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<ModConstraint> MOD_CONSTRAINTS = new CopyOnWriteArrayList<>();

    /** If true on a dedicated server, players without a valid handshake are kicked. */
    public static volatile boolean requireClientOnDedicatedServer = false;
    /** Config-style floor; combined with {@link #registerMinimumBuildVersion} / feature registrations. */
    public static volatile String minimumModVersion = "1.0.0";
    public static volatile int handshakeTimeoutTicks = 200;

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

    private record ModConstraint(String modId, KrepapiVersionPolicy.Constraint constraint) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!requireClientOnDedicatedServer || !server.isDedicated()) {
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
                if (t >= handshakeTimeoutTicks) {
                    HANDSHAKE_TICKS.remove(id);
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
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiKeyActionC2SPayload.ID, (payload, context) -> {
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            long nonce = server.getOverworld().getRandom().nextLong();
            byte flags = requireClientOnDedicatedServer && server.isDedicated()
                    ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                    : 0;
            String cfg = minimumModVersion;
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
            HANDSHAKE.remove(handler.player.getUuid());
            HANDSHAKE_TICKS.remove(handler.player.getUuid());
        });
    }

    public static void sendBindings(ServerPlayerEntity player, List<ProtocolMessages.BindingEntry> entries) {
        ArrayList<ProtocolMessages.BindingEntry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparing(ProtocolMessages.BindingEntry::actionId));
        ServerPlayNetworking.send(player, new KrepapiBindingsS2CPayload(copy));
    }
}
