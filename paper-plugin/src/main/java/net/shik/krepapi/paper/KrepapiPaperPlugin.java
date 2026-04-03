package net.shik.krepapi.paper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.shik.krepapi.protocol.KrepapiBuildVersion;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private final Map<UUID, PendingHandshake> pending = new ConcurrentHashMap<>();
    private final Map<Plugin, CopyOnWriteArrayList<KrepapiVersionPolicy.Constraint>> constraintsByPlugin = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerChannels();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_CLIENT_INFO, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_KEY_ACTION, this);
        getLogger().info("KrepAPI Paper reference enabled. Channels: " + KrepapiChannels.S2C_HELLO + ", ...");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_CLIENT_INFO);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_KEY_ACTION);
        unregisterOutgoing();
        constraintsByPlugin.clear();
    }

    /**
     * Version requirements API for other plugins (constraints cleared when {@code plugin} disables).
     */
    public KrepapiPaperVersionGate versionGate(@NotNull Plugin plugin) {
        return new KrepapiPaperVersionGate(this, plugin);
    }

    void registerVersionConstraint(@NotNull Plugin owner, @NotNull KrepapiVersionPolicy.Constraint constraint) {
        constraintsByPlugin.computeIfAbsent(owner, p -> new CopyOnWriteArrayList<>()).add(constraint);
    }

    private List<KrepapiVersionPolicy.Constraint> snapshotConstraints() {
        return constraintsByPlugin.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    private void registerChannels() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, KrepapiChannels.S2C_HELLO);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KrepapiChannels.S2C_BINDINGS);
    }

    private void unregisterOutgoing() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_HELLO);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_BINDINGS);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        constraintsByPlugin.remove(event.getPlugin());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("send-hello-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        long nonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        byte flags = getConfig().getBoolean("require-krepapi", true)
                ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                : 0;
        String configMin = getConfig().getString("minimum-mod-version", "1.0.0");
        List<KrepapiVersionPolicy.Constraint> snap = snapshotConstraints();
        String effectiveMin = KrepapiVersionPolicy.effectiveMinimum(configMin, snap);
        pending.put(player.getUniqueId(), new PendingHandshake(nonce, effectiveMin, configMin, snap, false));

        byte[] payload = ProtocolMessages.encodeHello(new ProtocolMessages.Hello(
                KrepapiProtocolVersion.CURRENT,
                flags,
                effectiveMin,
                nonce
        ));
        player.sendPluginMessage(this, KrepapiChannels.S2C_HELLO, payload);

        long delay = getConfig().getLong("handshake-timeout-ticks", 200L);
        if (getConfig().getBoolean("require-krepapi", true)) {
            getServer().getScheduler().runTaskLater(this, () -> checkTimeout(player.getUniqueId()), delay);
        }

        if (getConfig().getBoolean("example-bindings", true)) {
            getServer().getScheduler().runTaskLater(this, () -> sendExampleBindings(player), 40L);
        }
    }

    private void checkTimeout(UUID id) {
        PendingHandshake h = pending.get(id);
        if (h == null || h.answered) {
            return;
        }
        Player p = getServer().getPlayer(id);
        if (p != null && p.isOnline()) {
            p.kick(Component.text(KrepapiKickReasons.HANDSHAKE_TIMEOUT));
        }
        pending.remove(id);
    }

    private void sendExampleBindings(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PendingHandshake h = pending.get(player.getUniqueId());
        if (getConfig().getBoolean("require-krepapi", true) && (h == null || !h.answered)) {
            return;
        }
        var entries = java.util.List.of(
                new ProtocolMessages.BindingEntry(
                        "example_emote",
                        "Example emote",
                        71,
                        false,
                        "krepapi.example"
                )
        );
        player.sendPluginMessage(this, KrepapiChannels.S2C_BINDINGS, ProtocolMessages.encodeBindingsSync(new ProtocolMessages.BindingsSync(entries)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (KrepapiChannels.C2S_CLIENT_INFO.equals(channel)) {
            onClientInfo(player, message);
        } else if (KrepapiChannels.C2S_KEY_ACTION.equals(channel)) {
            onKeyAction(player, message);
        }
    }

    private void onClientInfo(Player player, byte[] message) {
        ProtocolMessages.ClientInfo info;
        try {
            info = ProtocolMessages.decodeClientInfo(message);
        } catch (RuntimeException ex) {
            getLogger().warning("Bad client_info from " + player.getName() + ": " + ex.getMessage());
            return;
        }
        PendingHandshake h = pending.get(player.getUniqueId());
        if (h == null || h.nonce != info.challengeNonce()) {
            return;
        }
        h.answered = true;
        if (info.protocolVersion() != KrepapiProtocolVersion.CURRENT) {
            player.kick(Component.text(KrepapiKickReasons.PROTOCOL_MISMATCH));
            return;
        }
        if (!KrepapiBuildVersion.isAtLeast(info.modVersion(), h.effectiveMin)) {
            KrepapiVersionPolicy.Constraint fail = KrepapiVersionPolicy.strictestFailure(
                    info.modVersion(),
                    h.configMin,
                    h.constraintsSnapshot
            );
            String kick;
            if (fail != null && fail.featureId() != null) {
                kick = KrepapiKickReasons.modBuildVersionTooOldForFeature(fail.featureId(), fail.minimumBuildVersion());
            } else if (fail != null) {
                kick = KrepapiKickReasons.modBuildVersionTooOld(fail.minimumBuildVersion());
            } else {
                kick = KrepapiKickReasons.modBuildVersionTooOld(h.effectiveMin);
            }
            player.kick(Component.text(kick));
        }
    }

    private void onKeyAction(Player player, byte[] message) {
        try {
            ProtocolMessages.KeyAction a = ProtocolMessages.decodeKeyAction(message);
            getLogger().info("[KrepAPI] " + player.getName() + " key " + a.actionId() + " seq=" + a.sequence());
        } catch (RuntimeException ex) {
            getLogger().warning("Bad key_action from " + player.getName());
        }
    }

    private static final class PendingHandshake {
        final long nonce;
        final String effectiveMin;
        final String configMin;
        final List<KrepapiVersionPolicy.Constraint> constraintsSnapshot;
        volatile boolean answered;

        PendingHandshake(
                long nonce,
                String effectiveMin,
                String configMin,
                List<KrepapiVersionPolicy.Constraint> constraintsSnapshot,
                boolean answered
        ) {
            this.nonce = nonce;
            this.effectiveMin = effectiveMin;
            this.configMin = configMin;
            this.constraintsSnapshot = constraintsSnapshot;
            this.answered = answered;
        }
    }
}
