package com.duox.storagemanager;

import com.duox.storagemanager.gui.UtilityGui;
import com.duox.storagemanager.system.*;
import com.duox.storagemanager.system.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/* Main mod entry point. */

public class StorageManager implements ClientModInitializer {

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.parse("storage_manager"));

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            InputConstants.KEY_RSHIFT,
            KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        ModuleManager.INSTANCE.init();
        KeyMappingHelper.registerKeyMapping(OPEN_GUI_KEY);
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            KeyMappingHelper.registerKeyMapping(module.getKeyMapping());
        }
        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager.INSTANCE::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI_KEY.consumeClick()) client.gui.setScreen(new UtilityGui());
        });
    }
}
