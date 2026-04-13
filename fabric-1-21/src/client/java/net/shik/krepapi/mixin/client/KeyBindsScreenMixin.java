package net.shik.krepapi.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.client.ServerBindingManager;

@Mixin(KeyBindsScreen.class)
public class KeyBindsScreenMixin {

    @Shadow
    @Final
    private KeyBindsList keyBindsList;

    @Inject(method = "addFooter", at = @At("TAIL"))
    private void krepapi$filterGridBindingRows(CallbackInfo ci) {
        KeyBindsList list = this.keyBindsList;
        List<KeyBindsList.Entry> remove = new ArrayList<>();
        for (KeyBindsList.Entry e : new ArrayList<>(list.children())) {
            if (e instanceof KeyBindsList.KeyEntry ke) {
                KeyMapping km = ((KeyBindsListKeyEntryAccessor) ke).krepapi$getMapping();
                if (!ServerBindingManager.shouldShowGridKeyInControls(km)) {
                    remove.add(e);
                }
            }
        }
        for (KeyBindsList.Entry e : new ArrayList<>(list.children())) {
            if (e instanceof KeyBindsList.CategoryEntry ce) {
                KeyMapping.Category cat = ((KeyBindsListCategoryEntryMixin) (Object) ce).krepapi$getCategory();
                if (cat != null && isKrepapiGridCategory(cat.id())) {
                    int slot = gridSlotFromCategoryPath(cat.id().getPath());
                    if (slot >= 0 && !ServerBindingManager.categorySlotHasVisibleKeys(slot)) {
                        remove.add(e);
                    }
                }
            }
        }
        if (!remove.isEmpty()) {
            ((AbstractSelectionListInvoker) (Object) list).krepapi$removeEntries(remove);
        }
    }

    private static boolean isKrepapiGridCategory(Identifier id) {
        return id != null && "krepapi".equals(id.getNamespace()) && gridSlotFromCategoryPath(id.getPath()) >= 0;
    }

    /** Path {@code s00}…{@code s09} → slot index, or {@code -1}. */
    private static int gridSlotFromCategoryPath(String path) {
        if (path == null || path.length() != 3 || path.charAt(0) != 's') {
            return -1;
        }
        try {
            int v = Integer.parseInt(path.substring(1));
            if (v >= 0 && v < net.shik.krepapi.protocol.ProtocolMessages.GRID_CATEGORY_SLOTS) {
                return v;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }
}
