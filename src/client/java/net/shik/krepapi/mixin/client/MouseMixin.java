package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.shik.krepapi.client.MouseCaptureState;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void krepapi$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null && client.screen == null) {
            return;
        }
        if (MouseCaptureState.sendIfCapturingButton(client, button, action, mods)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void krepapi$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null && client.screen == null) {
            return;
        }
        if (MouseCaptureState.sendIfCapturingScroll(client, horizontal, vertical)) {
            ci.cancel();
        }
    }
}
