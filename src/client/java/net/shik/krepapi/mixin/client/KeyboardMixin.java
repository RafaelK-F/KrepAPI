package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyInput;
import net.shik.krepapi.client.KrepapiKeyPipeline;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void krepapi$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null && client.screen == null) {
            return;
        }
        KeyInput input = new KeyInput(key, scancode, modifiers);
        if (KrepapiKeyPipeline.dispatch(client, input, action)) {
            ci.cancel();
        }
    }
}
