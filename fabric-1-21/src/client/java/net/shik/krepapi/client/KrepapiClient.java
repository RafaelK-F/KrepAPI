package net.shik.krepapi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.shik.krepapi.platform.KrepapiFabricClientPlatform;
import net.shik.krepapi.platform.KrepapiFabricClientPlatformImpl;

public class KrepapiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KrepapiFabricClientPlatform.install(KrepapiFabricClientPlatformImpl.INSTANCE);

        ServerBindingManager.ensurePoolInitialized();
        KrepapiClientNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(ServerBindingManager::tick);

        String modVersion = FabricLoader.getInstance()
                .getModContainer("krepapi").get()
                .getMetadata().getVersion().getFriendlyString();
        String mcVersion = SharedConstants.getCurrentVersion().name();
        UpdateChecker.checkAsync(modVersion, mcVersion);

        ClientTickEvents.END_CLIENT_TICK.register(KrepapiUpdateHud::tick);
        HudRenderCallback.EVENT.register(KrepapiUpdateHudOverlay::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("krepapi")
                                .then(ClientCommandManager.literal("menu")
                                        .executes(ctx -> {
                                            Minecraft.getInstance().execute(KrepapiFabricClientPlatform::openKrepapiMenuScreen);
                                            return 1;
                                        })
                                )
                )
        );
    }
}
