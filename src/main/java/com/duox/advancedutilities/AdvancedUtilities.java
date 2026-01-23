package com.duox.advancedutilities;

import com.duox.advancedutilities.gui.UtilityGui;
import com.duox.advancedutilities.system.*;
import com.duox.advancedutilities.system.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
@Mod("advancedutilities")
public class AdvancedUtilities {

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "Advanced Utilities"
    );

    /**
     * Constructs the mod instance and registers event handlers.
     *
     * @param modEventBus The mod event bus
     */
    public AdvancedUtilities(IEventBus modEventBus) {
        // Register mod lifecycle events
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerKeys);

        // Register game events (tick, input, etc.)
        NeoForge.EVENT_BUS.register(this);

        // Initialize systems
        BlockSelector.INSTANCE.init();
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
