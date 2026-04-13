package net.shik.krepapi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyBindsListKeyEntryAccessor {
    @Accessor("key")
    KeyMapping krepapi$getMapping();
}
