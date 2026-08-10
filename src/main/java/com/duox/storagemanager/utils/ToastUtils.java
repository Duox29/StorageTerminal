package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public class ToastUtils {
    /**
     * Gửi một thông báo Toast với Title và Message dạng String
     */
    public static void sendToast(String title, String message) {
        sendToast(title, Component.literal(message));
    }

    /**
     * Gửi một thông báo Toast hỗ trợ Component (có style/color)
     */
    public static void sendToast(String title, Component message) {
        Minecraft mc = Minecraft.getInstance();

        // SỬA KIỂU DỮ LIỆU Ở ĐÂY THÀNH ToastManager
        ToastManager toastManager = mc.gui.toastManager();

        if (toastManager != null) {
            SystemToast.add(toastManager, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title), message);
        }
    }
}