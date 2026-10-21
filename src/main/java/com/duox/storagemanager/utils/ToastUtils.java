package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

/* Toast notification helpers. */
public class ToastUtils {
    public static void sendToast(String title, String message) {
        sendToast(title, Component.literal(message));
    }

    public static void sendToast(String title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        ToastManager toastManager = mc.gui.toastManager();

        if (toastManager != null) {
            SystemToast.add(toastManager, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title), message);
        }
    }
}