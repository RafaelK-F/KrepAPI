package net.shik.krepapi.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Builds a server-driven {@link KeyMapping} using protocol {@link ProtocolMessages.BindingEntry#category()}.
 * Category labels in the controls UI resolve via language keys; see {@link ServerBindingLabels} for the
 * {@code key.category.*} / {@code key.categories.*} pair injected per category id.
 */
final class KeyMappingCompat {
    private KeyMappingCompat() {
    }

    static KeyMapping createServerBinding(String storageTranslationKey, int keyCode, ProtocolMessages.BindingEntry entry) {
        return new KeyMapping(
                storageTranslationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                new KeyMapping.Category(categoryIdentifierFromProtocol(entry.category())));
    }

    /**
     * {@code namespace:path}, else first {@code '.'} splits namespace/path, else {@code krepapi:<sanitized token>}.
     */
    static Identifier categoryIdentifierFromProtocol(String raw) {
        if (raw == null || raw.isBlank()) {
            return Identifier.fromNamespaceAndPath("krepapi", "server");
        }
        String t = raw.trim();
        Identifier parsed = Identifier.tryParse(t);
        if (parsed != null) {
            return parsed;
        }
        int dot = t.indexOf('.');
        if (dot > 0) {
            String ns = t.substring(0, dot);
            String path = t.substring(dot + 1);
            if (Identifier.isValidNamespace(ns) && Identifier.isValidPath(path)) {
                return Identifier.fromNamespaceAndPath(ns, path);
            }
        }
        String safe = t.replaceAll("[^a-z0-9._-]", "_");
        if (safe.isEmpty()) {
            return Identifier.fromNamespaceAndPath("krepapi", "server");
        }
        if (!Identifier.isValidPath(safe)) {
            safe = "custom";
        }
        return Identifier.fromNamespaceAndPath("krepapi", safe);
    }
}
