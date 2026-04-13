package net.shik.krepapi.platform;

import java.util.Objects;

import net.minecraft.client.KeyMapping;

/**
 * Line-specific Fabric API entry points (1.21 vs 26.1). Install from {@link net.shik.krepapi.client.KrepapiClient}
 * before {@link net.shik.krepapi.client.ServerBindingManager#ensurePoolInitialized()}.
 */
public final class KrepapiFabricClientPlatform {

    public interface Hooks {
        void registerKeyMapping(KeyMapping km);

        /** Opens the in-game KrepAPI menu (implementation uses the correct Screen class per MC line). */
        void openKrepapiMenuScreen();
    }

    private static Hooks hooks;

    private KrepapiFabricClientPlatform() {
    }

    public static void install(Hooks h) {
        hooks = Objects.requireNonNull(h, "hooks");
    }

    public static void registerKeyMapping(KeyMapping km) {
        hooks.registerKeyMapping(km);
    }

    public static void openKrepapiMenuScreen() {
        hooks.openKrepapiMenuScreen();
    }
}
