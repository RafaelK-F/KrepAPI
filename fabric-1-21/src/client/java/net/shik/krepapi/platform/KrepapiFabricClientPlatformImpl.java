package net.shik.krepapi.platform;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.client.KrepapiMenuScreen;

/** Minecraft 1.21 Fabric API bindings. */
public final class KrepapiFabricClientPlatformImpl implements KrepapiFabricClientPlatform.Hooks {

    public static final KrepapiFabricClientPlatformImpl INSTANCE = new KrepapiFabricClientPlatformImpl();

    private KrepapiFabricClientPlatformImpl() {
    }

    @Override
    public void registerKeyMapping(KeyMapping km) {
        KeyBindingHelper.registerKeyBinding(km);
    }

    @Override
    public void openKrepapiMenuScreen() {
        Minecraft.getInstance().setScreen(KrepapiMenuScreen.create(null));
    }
}
