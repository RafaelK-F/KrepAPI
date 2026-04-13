package net.shik.krepapi.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.net.KrepapiKeyActionC2SPayload;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Server-driven {@link KeyMapping}s use a fixed {@value ProtocolMessages#GRID_TOTAL_KEYS}-key grid
 * ({@value ProtocolMessages#GRID_CATEGORY_SLOTS}×{@value ProtocolMessages#GRID_KEYS_PER_CATEGORY}) registered at client init.
 */
public final class ServerBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("krepapi");

    private static final KeyMapping.Category[] GRID_CATEGORIES = new KeyMapping.Category[ProtocolMessages.GRID_CATEGORY_SLOTS];

    static {
        for (int s = 0; s < ProtocolMessages.GRID_CATEGORY_SLOTS; s++) {
            GRID_CATEGORIES[s] = new KeyMapping.Category(
                    Identifier.fromNamespaceAndPath("krepapi", String.format("s%02d", s)));
        }
    }

    private static KeyMapping[] POOL;
    private static String[] POOL_LORE;
    private static boolean[] OCCUPIED;
    private static volatile boolean poolInitialized;

    private static final Map<String, KeyMapping> ACTIVE = new HashMap<>();
    private static final Map<String, Boolean> PREV_HELD = new HashMap<>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ServerBindingManager() {
    }

    public static void ensurePoolInitialized() {
        if (poolInitialized) {
            return;
        }
        synchronized (ServerBindingManager.class) {
            if (poolInitialized) {
                return;
            }
            POOL = new KeyMapping[ProtocolMessages.GRID_TOTAL_KEYS];
            POOL_LORE = new String[ProtocolMessages.GRID_TOTAL_KEYS];
            OCCUPIED = new boolean[ProtocolMessages.GRID_TOTAL_KEYS];
            for (int c = 0; c < ProtocolMessages.GRID_CATEGORY_SLOTS; c++) {
                for (int k = 0; k < ProtocolMessages.GRID_KEYS_PER_CATEGORY; k++) {
                    int flat = ProtocolMessages.flatGridIndex(c, k);
                    KeyMapping km = new KeyMapping(
                            gridTranslationKey(c, k),
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_UNKNOWN,
                            GRID_CATEGORIES[c]);
                    KeyBindingHelper.registerKeyBinding(km);
                    POOL[flat] = km;
                }
            }
            poolInitialized = true;
        }
    }

    /** Translation / storage key for grid cell ({@code krepapi.category.{c}.key.{k}}). */
    public static String gridTranslationKey(int categorySlot, int keySlot) {
        return "krepapi.category." + categorySlot + ".key." + keySlot;
    }

    static KeyMapping getKeyMapping(String actionId) {
        return ACTIVE.get(actionId);
    }

    /** True if any server-defined binding is active (any grid cell occupied). */
    public static boolean hasActiveGridBindings() {
        return !ACTIVE.isEmpty();
    }

    /**
     * Flat pool index {@code 0 … GRID_TOTAL_KEYS-1} from {@link KeyMapping#getName()}, or {@code -1} if not a grid key.
     */
    public static int gridFlatIndexFromMappingName(String name) {
        if (name == null || !name.startsWith("krepapi.category.")) {
            return -1;
        }
        try {
            int afterCat = "krepapi.category.".length();
            int dotKey = name.indexOf(".key.", afterCat);
            if (dotKey < 0) {
                return -1;
            }
            int cat = Integer.parseInt(name.substring(afterCat, dotKey));
            int ks = Integer.parseInt(name.substring(dotKey + ".key.".length()));
            if (cat < 0 || cat >= ProtocolMessages.GRID_CATEGORY_SLOTS
                    || ks < 0 || ks >= ProtocolMessages.GRID_KEYS_PER_CATEGORY) {
                return -1;
            }
            return ProtocolMessages.flatGridIndex(cat, ks);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean shouldShowGridKeyInControls(KeyMapping mapping) {
        int flat = gridFlatIndexFromMappingName(mapping.getName());
        if (flat < 0) {
            return true;
        }
        return flat < ProtocolMessages.GRID_TOTAL_KEYS && OCCUPIED != null && OCCUPIED[flat];
    }

    public static Optional<String> poolRowLore(KeyMapping mapping) {
        if (POOL_LORE == null) {
            return Optional.empty();
        }
        int flat = gridFlatIndexFromMappingName(mapping.getName());
        if (flat < 0 || flat >= ProtocolMessages.GRID_TOTAL_KEYS) {
            return Optional.empty();
        }
        String s = POOL_LORE[flat];
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    /** Category slot {@code 0…9} has at least one occupied key cell. */
    public static boolean categorySlotHasVisibleKeys(int categorySlot) {
        if (categorySlot < 0 || categorySlot >= ProtocolMessages.GRID_CATEGORY_SLOTS || OCCUPIED == null) {
            return false;
        }
        for (int k = 0; k < ProtocolMessages.GRID_KEYS_PER_CATEGORY; k++) {
            if (OCCUPIED[ProtocolMessages.flatGridIndex(categorySlot, k)]) {
                return true;
            }
        }
        return false;
    }

    public static void applyBindings(Minecraft client, ProtocolMessages.BindingsGridSync sync) {
        ensurePoolInitialized();
        List<ProtocolMessages.GridBindingCell> unique = ProtocolMessages.dedupeGridCellsLastWins(sync.cells());
        if (unique.size() > ProtocolMessages.MAX_GRID_CELLS) {
            LOGGER.warn(
                    "KrepAPI: server sent {} grid cells; max is {}; truncating.",
                    unique.size(),
                    ProtocolMessages.MAX_GRID_CELLS);
            unique = unique.subList(0, ProtocolMessages.MAX_GRID_CELLS);
        }
        clear(client);
        ServerBindingLabels.apply(client, sync.categoryTitles(), unique);
        for (ProtocolMessages.GridBindingCell e : unique) {
            int flat = ProtocolMessages.flatGridIndex(e.categorySlot(), e.keySlot());
            KeyMapping km = POOL[flat];
            KeyMappingCompat.reconfigurePoolMapping(km, e);
            ACTIVE.put(e.actionId(), km);
            String lore = e.lore();
            POOL_LORE[flat] = (lore == null || lore.isBlank()) ? null : lore;
            OCCUPIED[flat] = true;
        }
        KrepapiKeyPipeline.setServerOverrideBindings(unique);
        KrepapiDebugLog.bindingsApplied(unique.stream().map(ProtocolMessages.GridBindingCell::actionId).toList());
    }

    public static void clear(Minecraft client) {
        KrepapiKeyPipeline.clearServerOverrides();
        for (KeyMapping km : ACTIVE.values()) {
            KeyMappingCompat.resetPoolMapping(km);
        }
        ServerBindingLabels.clear(client);
        ACTIVE.clear();
        PREV_HELD.clear();
        if (POOL_LORE != null) {
            Arrays.fill(POOL_LORE, null);
        }
        if (OCCUPIED != null) {
            Arrays.fill(OCCUPIED, false);
        }
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        for (Map.Entry<String, KeyMapping> e : ACTIVE.entrySet()) {
            String actionId = e.getKey();
            KeyMapping km = e.getValue();
            boolean down = km.isDown();
            boolean prev = Boolean.TRUE.equals(PREV_HELD.get(actionId));
            if (!prev && down) {
                sendKeyAction(actionId, ProtocolMessages.KeyAction.PHASE_PRESS);
            } else if (prev && !down) {
                sendKeyAction(actionId, ProtocolMessages.KeyAction.PHASE_RELEASE);
            }
            PREV_HELD.put(actionId, down);
        }
    }

    private static void sendKeyAction(String actionId, byte phase) {
        int seq = SEQUENCE.incrementAndGet();
        KrepapiDebugLog.keyActionSent(actionId,
                phase == ProtocolMessages.KeyAction.PHASE_PRESS ? "press" : "release", seq);
        ClientPlayNetworking.send(new KrepapiKeyActionC2SPayload(actionId, phase, seq));
    }

    private static String sanitize(String actionId) {
        return actionId.replaceAll("[^a-z0-9._-]", "_");
    }

    static String bindingStorageTranslationKey(String actionId) {
        return "krepapi.server." + sanitize(actionId);
    }
}
