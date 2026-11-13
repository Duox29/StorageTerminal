package com.duox.storagemanager.utils;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/* Toast notification helpers. */
public class ToastUtils {
    public static void sendToast(String title, String message) {
        sendToast(title, Component.literal(message));
    }

    public static void sendToast(String title, Component message) {
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null && !autoStash.isSendToasts()) return;

        Minecraft mc = Minecraft.getInstance();
        ToastComponent toastManager = mc.getToasts();

        if (toastManager != null) {
            SystemToast.add(toastManager, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title), message);
        }
    }
}
