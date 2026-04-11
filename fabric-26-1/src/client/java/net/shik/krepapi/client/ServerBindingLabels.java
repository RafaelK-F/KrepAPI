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
 * Injects {@link ProtocolMessages.BindingEntry#displayName()} and category titles into the active client
 * {@code Language} string table so {@link net.minecraft.client.KeyMapping} translation keys resolve at runtime.
 * <p>
 * For each category {@link Identifier}, both {@code key.category.<ns>.<path>} and {@code key.categories.<ns>.<path>}
 * (path segments {@code /} → {@code .}) are set to the same string so controls grouping matches vanilla across
 * versions that look up either prefix.
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

    static void apply(Minecraft client, List<ProtocolMessages.BindingEntry> entries) {
        if (client == null || entries.isEmpty()) {
            return;
        }
        Map<String, String> map = editableLanguageMap(client);
        if (map == null) {
            return;
        }
        Map<String, String> add = new HashMap<>();
        for (ProtocolMessages.BindingEntry e : entries) {
            String bindKey = ServerBindingManager.bindingStorageTranslationKey(e.actionId());
            String row = e.displayName().isBlank() ? e.actionId() : e.displayName();
            add.put(bindKey, row);

            Identifier cat = KeyMappingCompat.categoryIdentifierFromProtocol(e.category());
            boolean hasRawCategory = e.category() != null && !e.category().isBlank();
            String catText = hasRawCategory ? e.category().trim() : defaultCategoryTitle(cat);
            String suffix = cat.getNamespace() + "." + cat.getPath().replace('/', '.');
            add.putIfAbsent("key.category." + suffix, catText);
            add.putIfAbsent("key.categories." + suffix, catText);
        }
        for (Map.Entry<String, String> en : add.entrySet()) {
            map.put(en.getKey(), en.getValue());
            INJECTED.add(en.getKey());
        }
    }

    private static String defaultCategoryTitle(Identifier cat) {
        if ("krepapi".equals(cat.getNamespace()) && "server".equals(cat.getPath())) {
            return "KrepAPI (this server)";
        }
        return cat.getNamespace() + "/" + cat.getPath();
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
