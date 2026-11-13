package com.duox.storagemanager;

import com.duox.storagemanager.gui.UtilityGui;
import com.duox.storagemanager.system.*;
import com.duox.storagemanager.system.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/* Main mod entry point. */

public class StorageManager implements ClientModInitializer {

    public static final String KEY_CATEGORY = "Storage Manager";

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            InputConstants.KEY_RSHIFT,
            KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        ModuleManager.INSTANCE.init();
        KeyBindingHelper.registerKeyBinding(OPEN_GUI_KEY);
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            KeyBindingHelper.registerKeyBinding(module.getKeyMapping());
        }
        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager.INSTANCE::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI_KEY.consumeClick()) client.setScreen(new UtilityGui());
        });
    }
}
