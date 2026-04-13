package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.shik.krepapi.client.MouseCaptureState;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void krepapi$onButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null && client.screen == null) {
            return;
        }
        if (MouseCaptureState.sendIfCapturingButton(client, input.button(), action, input.modifiers())) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void krepapi$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null && client.screen == null) {
            return;
        }
        if (MouseCaptureState.sendIfCapturingScroll(client, horizontal, vertical)) {
            ci.cancel();
        }
    }
}
