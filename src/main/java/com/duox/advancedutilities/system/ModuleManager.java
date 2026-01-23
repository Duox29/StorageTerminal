package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Manages all modules in the mod.
 * Handles registration, lookup, and tick processing for modules.
 */
public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    // Use a Map for O(1) lookup by class instead of looping
    private final Map<Class<? extends Module>, Module> moduleMap = new LinkedHashMap<>();

    private ModuleManager() {
        // Player category modules
        register(new AutoStash());
        register(new StorageManager());
    }

    /**
     * Registers a module instance.
     *
     * @param module The module to register
     */
    public void register(Module module) {
        moduleMap.put(module.getClass(), module);
    }

    /**
     * Gets a module by its class.
     *
     * @param clazz The class of the module to retrieve
     * @param <T> The type of module
     * @return The module instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleMap.get(clazz);
    }

    /**
     * Gets all registered modules.
     *
     * @return A list of all modules
     */
    public List<Module> getModules() {
        return new ArrayList<>(moduleMap.values());
    }

    /**
     * Sets the enabled state of a module and saves the configuration.
     *
     * @param module The module to modify
     * @param state The new enabled state
     */
    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigManager.getInstance().save();
    }

    /**
     * Gets all modules in a specific category.
     *
     * @param category The category to filter by
     * @return A list of modules in the specified category
     */
    public List<Module> getModulesByCategory(Category category) {
        return moduleMap.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Handles client tick events and calls onTick() for all enabled modules.
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player != null) {
            // Xử lý manual cache update cho AutoStash (luôn chạy, không phụ thuộc vào module enabled)
            AutoStash.tickManualCacheUpdate();

            // Handle module keybinds
            List<Module> toggledModules = new ArrayList<>();
            for (Module module : moduleMap.values()) {
                if (module.isHold()) {
                    boolean isKeyDown = module.getKeyMapping().isDown();
                    if (module.isEnabled() != isKeyDown) {
                         module.setEnabled(isKeyDown);
                         // Optional: Don't notify for hold modules to avoid spam
                         // toggledModules.add(module);
                    }
                    // Consume click to prevent it from accumulating
                    while (module.getKeyMapping().consumeClick()) {}
                } else {
                    while (module.getKeyMapping().consumeClick()) {
                        module.setEnabled(!module.isEnabled());
                        toggledModules.add(module);
                    }
                }
            }

            if (!toggledModules.isEmpty()) {
                net.minecraft.network.chat.MutableComponent message = net.minecraft.network.chat.Component.empty();
                for (int i = 0; i < toggledModules.size(); i++) {
                    Module m = toggledModules.get(i);
                    if (i > 0) message.append(net.minecraft.network.chat.Component.literal(", ").withStyle(net.minecraft.ChatFormatting.GRAY));
                    
                    message.append(net.minecraft.network.chat.Component.literal(m.getName())
                            .withStyle(m.isEnabled() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                }
                Minecraft.getInstance().gui.setOverlayMessage(message, false);
            }

            moduleMap.values().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }

    /**
     * Initializes the module manager.
     * Registers event handlers and loads configuration.
     */
    public void init() {
        NeoForge.EVENT_BUS.register(this);
        // Note: Load config AFTER modules are registered externally
        ConfigManager.getInstance().load();
    }
}
