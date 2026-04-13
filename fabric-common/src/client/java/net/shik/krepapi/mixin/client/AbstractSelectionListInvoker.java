package net.shik.krepapi.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.AbstractSelectionList;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListInvoker {
    @Invoker("removeEntries")
    void krepapi$removeEntries(List<?> entries);
}
