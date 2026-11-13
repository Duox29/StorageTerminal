package com.duox.storagemanager.gui;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Mouse input captured at the 1.21.1 screen callback boundary. */
public record MouseButtonEvent(double x, double y, int button, int modifiers) {
    public static MouseButtonEvent capture(double x, double y, int button) {
        int modifiers = (Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0)
                | (Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
        return new MouseButtonEvent(x, y, button, modifiers);
    }

    public boolean hasShiftDown() { return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0; }
    public boolean hasControlDown() { return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0; }
    public boolean hasAltDown() { return (modifiers & GLFW.GLFW_MOD_ALT) != 0; }
}
