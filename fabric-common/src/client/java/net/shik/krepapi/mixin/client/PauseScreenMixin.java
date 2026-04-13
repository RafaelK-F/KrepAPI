package net.shik.krepapi.mixin.client;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.shik.krepapi.client.InterceptKeyState;
import net.shik.krepapi.protocol.ProtocolMessages;

@Mixin(Screen.class)
public class PauseScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void krepapi$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof PauseScreen)) {
            return;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE
                && InterceptKeyState.blockVanillaForSlot(ProtocolMessages.INTERCEPT_SLOT_ESCAPE)) {
            cir.setReturnValue(true);
        }
    }
}
