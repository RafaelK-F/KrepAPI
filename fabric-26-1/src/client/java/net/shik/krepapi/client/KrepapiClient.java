package net.shik.krepapi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

public class KrepapiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KrepapiClientNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(ServerBindingManager::tick);

        String modVersion = FabricLoader.getInstance()
                .getModContainer("krepapi").get()
                .getMetadata().getVersion().getFriendlyString();
        String mcVersion = SharedConstants.getCurrentVersion().getName();
        UpdateChecker.checkAsync(modVersion, mcVersion);

        ClientTickEvents.END_CLIENT_TICK.register(KrepapiUpdateHud::tick);
        HudRenderCallback.EVENT.register(KrepapiUpdateHud::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("krepapi")
                                .then(ClientCommandManager.literal("menu")
                                        .executes(ctx -> {
                                            Minecraft.getInstance().execute(() ->
                                                    Minecraft.getInstance().setScreen(
                                                            KrepapiMenuScreen26.create(null)));
                                            return 1;
                                        })
                                )
                )
        );
    }
}
