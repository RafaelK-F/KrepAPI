package net.shik.krepapi.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Injects grid row labels and category titles into the active client language map so {@link net.minecraft.client.KeyMapping}
 * keys resolve at runtime.
 */
final class ServerBindingLabels {
    private static final Set<String> INJECTED = new HashSet<>();

    private ServerBindingLabels() {
    }

    static void clear(Minecraft client) {
        if (!INJECTED.isEmpty() && client != null) {
            Map<String, String> map = editableLanguageMap(client);
            if (map != null) {
                for (String k : INJECTED) {
                    map.remove(k);
                }
            }
        }
        INJECTED.clear();
    }

    static void apply(Minecraft client, List<String> categoryTitles, List<ProtocolMessages.GridBindingCell> cells) {
        if (client == null) {
            return;
        }
        Map<String, String> map = editableLanguageMap(client);
        if (map == null) {
            return;
        }
        Map<String, String> add = new HashMap<>();
        if (categoryTitles != null) {
            for (int slot = 0; slot < ProtocolMessages.GRID_CATEGORY_SLOTS; slot++) {
                String title = slot < categoryTitles.size() ? categoryTitles.get(slot) : "";
                Identifier catId = Identifier.fromNamespaceAndPath("krepapi", String.format("s%02d", slot));
                String suffix = catId.getNamespace() + "." + catId.getPath().replace('/', '.');
                String catText = (title == null || title.isBlank()) ? defaultCategoryTitle(slot) : title.trim();
                add.putIfAbsent("key.category." + suffix, catText);
                add.putIfAbsent("key.categories." + suffix, catText);
            }
        }
        for (ProtocolMessages.GridBindingCell e : cells) {
            String bindKey = ServerBindingManager.gridTranslationKey(e.categorySlot(), e.keySlot());
            String row = e.displayName().isBlank() ? e.actionId() : e.displayName().trim();
            if (row.isBlank()) {
                row = "KrepAPI " + e.categorySlot() + "/" + e.keySlot();
            }
            add.put(bindKey, row);
        }
        for (Map.Entry<String, String> en : add.entrySet()) {
            map.put(en.getKey(), en.getValue());
            INJECTED.add(en.getKey());
        }
    }

    private static String defaultCategoryTitle(int slot) {
        return "KrepAPI (" + slot + ")";
    }

    private static Map<String, String> editableLanguageMap(Minecraft client) {
        try {
            Object language = invokeLanguage(client.getLanguageManager());
            if (language == null) {
                return null;
            }
            for (Field f : language.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object raw = f.get(language);
                if (!(raw instanceof Map<?, ?> m)) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> typed = (Map<String, String>) m;
                    typed.put("__krepapi_table_probe__", "");
                    typed.remove("__krepapi_table_probe__");
                    return typed;
                } catch (UnsupportedOperationException ignored) {
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return null;
    }

    private static Object invokeLanguage(Object languageManager) {
        try {
            Method get = languageManager.getClass().getMethod("getLanguage");
            return get.invoke(languageManager);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            for (Method m : languageManager.getClass().getMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                if (!"net.minecraft.client.resources.language.Language".equals(m.getReturnType().getName())) {
                    continue;
                }
                return m.invoke(languageManager);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
