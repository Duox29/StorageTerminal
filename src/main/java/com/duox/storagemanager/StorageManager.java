package com.duox.storagemanager;

import com.duox.storagemanager.gui.UtilityGui;
import com.duox.storagemanager.system.*;
import com.duox.storagemanager.system.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/* Main mod entry point. */
@Mod("storagemanager")
public class StorageManager {

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.parse("storage_manager"));

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KEY_CATEGORY
    );

    public StorageManager(IEventBus modEventBus) {
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ModuleManager.INSTANCE.init();
    }

    public void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            event.register(module.getKeyMapping());
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().gui.setScreen(new UtilityGui());
        }
    }
}