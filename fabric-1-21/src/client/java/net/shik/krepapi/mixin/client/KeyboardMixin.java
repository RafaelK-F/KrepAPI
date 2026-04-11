package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.shik.krepapi.client.KrepapiKeyPipeline;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void krepapi$keyPress(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (minecraft.player == null && minecraft.screen == null) {
            return;
        }
        if (KrepapiKeyPipeline.dispatch(minecraft, input, action)) {
            ci.cancel();
        }
    }
}
