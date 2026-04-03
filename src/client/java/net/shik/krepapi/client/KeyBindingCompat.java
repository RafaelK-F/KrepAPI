package net.shik.krepapi.client;

import java.lang.reflect.Constructor;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

/**
 * {@link KeyBinding} switched from a {@link String} category (1.21.4) to {@link KeyBinding.Category} (1.21.11+).
 * Picks the available constructor at runtime so one jar works across that range.
 */
final class KeyBindingCompat {
    private KeyBindingCompat() {
    }

    static KeyBinding createServerBinding(String translationKey, int keyCode) {
        try {
            Constructor<KeyBinding> ctor = KeyBinding.class.getConstructor(
                    String.class, InputUtil.Type.class, int.class, String.class);
            return ctor.newInstance(
                    translationKey,
                    InputUtil.Type.KEYSYM,
                    keyCode,
                    "key.categories.krepapi.server");
        } catch (NoSuchMethodException e) {
            return new KeyBinding(
                    translationKey,
                    InputUtil.Type.KEYSYM,
                    keyCode,
                    KeyBinding.Category.create(Identifier.of("krepapi", "server")));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
