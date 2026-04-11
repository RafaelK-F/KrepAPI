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
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.KrepapiVersionRequirement;
import net.shik.krepapi.protocol.KrepapiChannels;
import net.shik.krepapi.protocol.KrepapiKickReasons;
import net.shik.krepapi.protocol.KrepapiProtocolVersion;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.ProtocolMessages;

public final class KrepapiPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private final Map<UUID, PendingHandshake> pending = new ConcurrentHashMap<>();
    /** Capability bitfield from {@code c2s_client_info} after a successful handshake. */
    private final Map<UUID, Integer> clientCapabilities = new ConcurrentHashMap<>();
    private final Map<Plugin, CopyOnWriteArrayList<KrepapiVersionPolicy.Constraint>> constraintsByPlugin = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            KrepapiVersionRequirement.parse(getConfig().getString("minimum-mod-version", "1.2.0").trim());
        } catch (IllegalArgumentException ex) {
            getLogger().severe("Invalid minimum-mod-version in config.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerChannels();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_CLIENT_INFO, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_KEY_ACTION, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_RAW_KEY, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, KrepapiChannels.C2S_MOUSE_ACTION, this);
        getLogger().info("KrepAPI Paper reference enabled. Channels: " + KrepapiChannels.S2C_HELLO + ", ...");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_CLIENT_INFO);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_KEY_ACTION);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_RAW_KEY);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, KrepapiChannels.C2S_MOUSE_ACTION);
        unregisterOutgoing();
        constraintsByPlugin.clear();
        clientCapabilities.clear();
    }

    /**
     * Version requirements API for other plugins (constraints cleared when {@code plugin} disables).
     */
    public KrepapiPaperVersionGate versionGate(@NotNull Plugin plugin) {
        return new KrepapiPaperVersionGate(this, plugin);
    }

    void registerVersionConstraint(@NotNull Plugin owner, @NotNull KrepapiVersionPolicy.Constraint constraint) {
        try {
            KrepapiVersionRequirement.parse(constraint.minimumBuildVersion().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid KrepAPI version requirement for " + owner.getName() + ": "
                    + ex.getMessage(), ex);
        }
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
        getServer().getMessenger().registerOutgoingPluginChannel(this, KrepapiChannels.S2C_RAW_CAPTURE);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KrepapiChannels.S2C_INTERCEPT_KEYS);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KrepapiChannels.S2C_MOUSE_CAPTURE);
    }

    private void unregisterOutgoing() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_HELLO);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_BINDINGS);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_RAW_CAPTURE);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_INTERCEPT_KEYS);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, KrepapiChannels.S2C_MOUSE_CAPTURE);
    }

    /**
     * Sends {@link KrepapiChannels#S2C_RAW_CAPTURE} using the shared binary layout from {@link ProtocolMessages}.
     */
    public void sendRawCaptureConfig(@NotNull Player player, @NotNull ProtocolMessages.RawCaptureConfig config) {
        if (!player.isOnline()) {
            return;
        }
        byte[] payload = ProtocolMessages.encodeRawCaptureConfig(config);
        player.sendPluginMessage(this, KrepapiChannels.S2C_RAW_CAPTURE, payload);
    }

    /**
     * Sends {@link KrepapiChannels#S2C_INTERCEPT_KEYS}. An empty entry list clears intercept rules on the client.
     */
    public void sendInterceptKeys(@NotNull Player player, @NotNull ProtocolMessages.InterceptKeysSync sync) {
        if (!player.isOnline()) {
            return;
        }
        byte[] payload = ProtocolMessages.encodeInterceptKeysSync(sync);
        player.sendPluginMessage(this, KrepapiChannels.S2C_INTERCEPT_KEYS, payload);
    }

    /**
     * Sends {@link KrepapiChannels#S2C_MOUSE_CAPTURE} if the client advertised {@link KrepapiCapabilities#SERVER_MOUSE_CAPTURE}.
     */
    public void sendMouseCaptureConfig(@NotNull Player player, @NotNull ProtocolMessages.MouseCaptureConfig config) {
        if (!player.isOnline()) {
            return;
        }
        int caps = clientCapabilities.getOrDefault(player.getUniqueId(), 0);
        if ((caps & KrepapiCapabilities.SERVER_MOUSE_CAPTURE) == 0) {
            return;
        }
        byte[] payload = ProtocolMessages.encodeMouseCaptureConfig(config);
        player.sendPluginMessage(this, KrepapiChannels.S2C_MOUSE_CAPTURE, payload);
    }

    /**
     * Capability bitfield from the player's last successful {@code c2s_client_info}, or {@code 0} if unknown.
     */
    public int getClientCapabilities(@NotNull Player player) {
        return clientCapabilities.getOrDefault(player.getUniqueId(), 0);
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
        String configMin = getConfig().getString("minimum-mod-version", "1.2.0");
        List<KrepapiVersionPolicy.Constraint> snap = snapshotConstraints();
        try {
            KrepapiVersionPolicy.validateRequirements(configMin, snap);
        } catch (IllegalArgumentException ex) {
            getLogger().severe("Invalid KrepAPI version requirements: " + ex.getMessage());
            player.kick(Component.text("KrepAPI server version requirements are misconfigured."));
            return;
        }
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
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        clientCapabilities.remove(id);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!KrepapiChannels.isIncomingPlayChannel(channel)) {
            return;
        }
        if (KrepapiChannels.C2S_CLIENT_INFO.equals(channel)) {
            onClientInfo(player, message);
        } else if (KrepapiChannels.C2S_KEY_ACTION.equals(channel)) {
            onKeyAction(player, message);
        } else if (KrepapiChannels.C2S_RAW_KEY.equals(channel)) {
            onRawKey(player, message);
        } else if (KrepapiChannels.C2S_MOUSE_ACTION.equals(channel)) {
            onMouseAction(player, message);
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
        KrepapiVersionPolicy.VersionCheckFailure fail = KrepapiVersionPolicy.firstVersionCheckFailure(
                info.modVersion(),
                h.configMin,
                h.constraintsSnapshot
        );
        if (fail != null) {
            player.kick(Component.text(KrepapiKickReasons.forVersionCheckFailure(fail)));
            return;
        }
        clientCapabilities.put(player.getUniqueId(), info.capabilities());
    }

    private void onKeyAction(Player player, byte[] message) {
        try {
            ProtocolMessages.KeyAction a = ProtocolMessages.decodeKeyAction(message);
            String phaseName = switch (a.phase()) {
                case ProtocolMessages.KeyAction.PHASE_PRESS -> "press";
                case ProtocolMessages.KeyAction.PHASE_RELEASE -> "release";
                default -> "unknown(" + a.phase() + ")";
            };
            getLogger().info("[KrepAPI] " + player.getName() + " key " + a.actionId()
                    + " phase=" + phaseName + " seq=" + a.sequence());
        } catch (RuntimeException ex) {
            getLogger().warning("Bad key_action from " + player.getName());
        }
    }

    private void onRawKey(Player player, byte[] message) {
        try {
            ProtocolMessages.RawKeyEvent e = ProtocolMessages.decodeRawKeyEvent(message);
            String actionName = switch (e.glfwAction()) {
                case 0 -> "release";
                case 1 -> "press";
                case 2 -> "repeat";
                default -> "unknown(" + e.glfwAction() + ")";
            };
            getLogger().info("[KrepAPI] " + player.getName() + " raw_key key=" + e.key()
                    + " scancode=" + e.scancode() + " action=" + actionName + " seq=" + e.sequence());
        } catch (RuntimeException ex) {
            getLogger().warning("Bad raw_key from " + player.getName());
        }
    }

    private void onMouseAction(Player player, byte[] message) {
        try {
            ProtocolMessages.MouseActionEvent e = ProtocolMessages.decodeMouseAction(message);
            if (e.kind() == ProtocolMessages.MOUSE_ACTION_KIND_BUTTON) {
                String actionName = switch (e.glfwAction()) {
                    case 0 -> "release";
                    case 1 -> "press";
                    default -> "unknown(" + e.glfwAction() + ")";
                };
                getLogger().info("[KrepAPI] " + player.getName() + " mouse_action button=" + e.button()
                        + " action=" + actionName + " mods=" + e.modifiers() + " seq=" + e.sequence()
                        + " extras=" + e.extras() + " cursor=(" + e.cursorX() + "," + e.cursorY() + ")");
            } else if (e.kind() == ProtocolMessages.MOUSE_ACTION_KIND_SCROLL) {
                getLogger().info("[KrepAPI] " + player.getName() + " mouse_action scroll=("
                        + e.scrollDeltaX() + "," + e.scrollDeltaY() + ") seq=" + e.sequence()
                        + " extras=" + e.extras() + " cursor=(" + e.cursorX() + "," + e.cursorY() + ")");
            }
        } catch (RuntimeException ex) {
            getLogger().warning("Bad mouse_action from " + player.getName());
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
