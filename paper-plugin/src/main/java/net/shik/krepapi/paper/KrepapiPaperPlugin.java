package net.shik.krepapi.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private final Map<UUID, PendingHandshake> pending = new ConcurrentHashMap<>();

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
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("send-hello-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        long nonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        byte flags = getConfig().getBoolean("require-krepapi", true)
                ? ProtocolMessages.HELLO_FLAG_REQUIRE_RESPONSE
                : 0;
        String minVer = getConfig().getString("minimum-mod-version", "1.0");
        pending.put(player.getUniqueId(), new PendingHandshake(nonce, minVer, false));

        byte[] payload = ProtocolMessages.encodeHello(new ProtocolMessages.Hello(
                KrepapiProtocolVersion.CURRENT,
                flags,
                minVer,
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
        String min = getConfig().getString("minimum-mod-version", "1.0");
        if (info.modVersion().compareTo(min) < 0) {
            player.kick(Component.text(KrepapiKickReasons.MOD_VERSION_TOO_OLD));
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
        @SuppressWarnings("unused")
        final String minModVersion;
        volatile boolean answered;

        PendingHandshake(long nonce, String minModVersion, boolean answered) {
            this.nonce = nonce;
            this.minModVersion = minModVersion;
            this.answered = answered;
        }
    }
}
