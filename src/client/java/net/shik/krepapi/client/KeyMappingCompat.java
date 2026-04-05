package net.shik.krepapi.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds a server-driven {@link KeyMapping} with a dedicated category (Minecraft 26.x / Mojang mappings).
 */
final class KeyMappingCompat {
    private KeyMappingCompat() {
    }

    static KeyMapping createServerBinding(String translationKey, int keyCode) {
        return new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                KeyMapping.Category.create(ResourceLocation.fromNamespaceAndPath("krepapi", "server")));
    }
}
