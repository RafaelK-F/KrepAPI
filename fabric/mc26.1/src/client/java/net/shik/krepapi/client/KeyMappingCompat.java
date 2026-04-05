package net.shik.krepapi.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Builds a server-driven {@link KeyMapping} with a dedicated category (official Mojang names via Loom).
 */
final class KeyMappingCompat {
    private KeyMappingCompat() {
    }

    static KeyMapping createServerBinding(String translationKey, int keyCode) {
        return new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath("krepapi", "server")));
    }
}
