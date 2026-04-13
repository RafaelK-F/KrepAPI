package net.shik.krepapi.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import net.shik.krepapi.client.ServerBindingManager;

@Mixin(KeyBindsList.KeyEntry.class)
public class KeyBindsListKeyEntryMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void krepapi$poolRowLoreTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
        if (!hovering) {
            return;
        }
        ServerBindingManager.poolRowLore(((KeyBindsListKeyEntryAccessor) (Object) this).krepapi$getMapping())
                .ifPresent(text -> guiGraphics.renderComponentTooltip(
                        Minecraft.getInstance().font,
                        List.of(Component.literal(text)),
                        mouseX,
                        mouseY));
    }
}
