package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.shik.krepapi.client.InterceptKeyState;
import net.shik.krepapi.protocol.ProtocolMessages;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "openGameMenu", at = @At("HEAD"), cancellable = true)
    private void krepapi$openGameMenu(boolean pauseOnly, CallbackInfo ci) {
        if (pauseOnly) {
            return;
        }
        if (InterceptKeyState.blockVanillaForSlot(ProtocolMessages.INTERCEPT_SLOT_ESCAPE)) {
            ci.cancel();
        }
    }
}
