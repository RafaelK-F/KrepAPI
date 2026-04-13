package net.shik.krepapi.platform;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.client.KrepapiMenuScreen26;

/** Minecraft 26.1 Fabric API bindings. */
public final class KrepapiFabricClientPlatformImpl implements KrepapiFabricClientPlatform.Hooks {

    public static final KrepapiFabricClientPlatformImpl INSTANCE = new KrepapiFabricClientPlatformImpl();

    private KrepapiFabricClientPlatformImpl() {
    }

    @Override
    public void registerKeyMapping(KeyMapping km) {
        KeyMappingHelper.registerKeyMapping(km);
    }

    @Override
    public void openKrepapiMenuScreen() {
        Minecraft.getInstance().setScreen(KrepapiMenuScreen26.create(null));
    }
}
