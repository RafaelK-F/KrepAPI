package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.shik.krepapi.client.KrepapiKeyPipeline;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void krepapi$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null && client.currentScreen == null) {
            return;
        }
        if (KrepapiKeyPipeline.dispatch(client, input, action)) {
            ci.cancel();
        }
    }
}
