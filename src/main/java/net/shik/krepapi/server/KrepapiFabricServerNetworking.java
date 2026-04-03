package net.shik.krepapi.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shik.krepapi.net.KrepapiBindingsS2CPayload;
import net.shik.krepapi.net.KrepapiClientInfoC2SPayload;
import net.shik.krepapi.net.KrepapiHelloS2CPayload;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiFabricServerNetworking {
    public static final KrepapiFabricHandshakeState HANDSHAKE = new KrepapiFabricHandshakeState();
    private static final Map<UUID, Integer> HANDSHAKE_TICKS = new ConcurrentHashMap<>();

    /** If true on a dedicated server, players without a valid handshake are kicked. */
    public static volatile boolean requireClientOnDedicatedServer = false;
    public static volatile String minimumModVersion = "1.0";
    public static volatile int handshakeTimeoutTicks = 200;

    private KrepapiFabricServerNetworking() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!requireClientOnDedicatedServer || !server.isDedicatedServer()) {
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

        ServerPlayNetworking.registerGlobalReceiver(KrepapiClientInfoC2SPayload.TYPE, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!HANDSHAKE.markAnswered(player.getUuid(), payload.challengeNonce())) {
                return;
            }
            if (payload.protocolVersion() != KrepapiProtocolVersion.CURRENT) {
                player.networkHandler.disconnect(Text.literal(KrepapiKickReasons.PROTOCOL_MISMATCH));
                return;
            }
            if (payload.modVersion().compareTo(minimumModVersion) < 0) {
                player.networkHandler.disconnect(Text.literal(KrepapiKickReasons.MOD_VERSION_TOO_OLD));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(KrepapiKeyActionC2SPayload.TYPE, (payload, context) -> {
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            long nonce = server.getOverworld().getRandom().nextLong();
            byte flags = requireClientOnDedicatedServer && server.isDedicatedServer()
                    ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                    : 0;
            HANDSHAKE.begin(player.getUuid(), nonce, minimumModVersion, flags != 0);
            HANDSHAKE_TICKS.put(player.getUuid(), 0);
            ServerPlayNetworking.send(player, new KrepapiHelloS2CPayload(
                    KrepapiProtocolVersion.CURRENT,
                    flags,
                    minimumModVersion,
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
