package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/* Client-side chat output. */
public class MessageUtils {
    public static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
