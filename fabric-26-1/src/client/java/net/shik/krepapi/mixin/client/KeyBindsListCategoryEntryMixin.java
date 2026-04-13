package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

@Mixin(KeyBindsList.CategoryEntry.class)
public class KeyBindsListCategoryEntryMixin {

    @Unique
    private KeyMapping.Category krepapi$capturedCategory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void krepapi$ctor(KeyBindsList parent, KeyMapping.Category category, CallbackInfo ci) {
        this.krepapi$capturedCategory = category;
    }

    public KeyMapping.Category krepapi$getCategory() {
        return this.krepapi$capturedCategory;
    }
}
