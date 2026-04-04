package net.shik.krepapi.mixin.client;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.input.KeyInput;
import net.shik.krepapi.client.InterceptKeyState;
import net.shik.krepapi.protocol.ProtocolMessages;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void krepapi$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE
                && InterceptKeyState.blockVanillaForSlot(ProtocolMessages.INTERCEPT_SLOT_ESCAPE)) {
            cir.setReturnValue(true);
        }
    }
}
