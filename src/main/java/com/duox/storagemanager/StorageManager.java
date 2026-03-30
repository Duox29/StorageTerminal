package com.duox.storagemanager;

import com.duox.storagemanager.gui.UtilityGui;
import com.duox.storagemanager.system.*;
import com.duox.storagemanager.system.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier; // FIX: Added Import
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/*
 * Main mod class for Advanced Utilities.
 * Initializes all systems and handles mod lifecycle events.
 */
@Mod("storagemanager")
public class StorageManager {

    // FIX: Register the KeyBinding Category (Must use Identifier)
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.parse("storage_manager"));

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KEY_CATEGORY // FIX: Pass the Category object, not a String
    );

    /**
     * Constructs the mod instance and registers event handlers.
     *
     * @param modEventBus The mod event bus
     */
    public StorageManager(IEventBus modEventBus) {
        // Register mod lifecycle events
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerKeys);

        // Register game events (tick, input, etc.)
        NeoForge.EVENT_BUS.register(this);

    }

    /**
     * Called during client setup phase.
     * Initializes modules, loads configuration, and sets up rendering.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        ModuleManager.INSTANCE.init();
        ConfigManager.getInstance().load();
    }

    /**
     * Registers key mappings for the mod.
     *
     * @param event The key mapping registration event
     */
    public void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
        // Register module keybinds
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            event.register(module.getKeyMapping());
        }
    }

    /**
     * Handles key input events.
     * Opens the GUI when the configured key is pressed.
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new UtilityGui());
        }
    }
}