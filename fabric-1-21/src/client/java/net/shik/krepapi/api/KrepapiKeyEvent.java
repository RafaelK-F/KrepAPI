package net.shik.krepapi.api;

import net.minecraft.client.Minecraft;

/**
 * A raw GLFW-style key event on the client, before vanilla key bindings run.
 */
public record KrepapiKeyEvent(Minecraft client, int key, int scancode, int action, int modifiers) {
    /** {@link org.lwjgl.glfw.GLFW#GLFW_PRESS} etc. */
    public int glfwAction() {
        return action;
    }
}
