package net.shik.krepapi.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.shik.krepapi.protocol.ProtocolMessages;

/**
 * Reconfigures a pre-registered pool {@link KeyMapping} from a {@link ProtocolMessages.GridBindingCell}.
 */
final class KeyMappingCompat {
    private KeyMappingCompat() {
    }

    static void reconfigurePoolMapping(KeyMapping km, ProtocolMessages.GridBindingCell entry) {
        InputConstants.Key k = InputConstants.Type.KEYSYM.getOrCreate(entry.defaultKey());
        km.setKey(k);
    }

    static void resetPoolMapping(KeyMapping km) {
        InputConstants.Key unk = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UNKNOWN);
        km.setKey(unk);
    }
}
