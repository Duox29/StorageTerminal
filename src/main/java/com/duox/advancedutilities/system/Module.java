package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

/*
 * Base class for all modules in the Advanced Utilities mod.
 * Modules are the core functionality units that can be enabled/disabled and configured.
 */
public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled = false;
    private final KeyMapping keyMapping;

    private final List<Setting<?>> settings = new ArrayList<>();

    /**
     * Creates a new module with the specified name, description, and category.
     *
     * @param name The display name of the module
     * @param description A brief description of what the module does
     * @param category The category this module belongs to
     */
    private boolean hold = false;

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyMapping = new KeyMapping(
                "key.advancedutilities.module." + name.toLowerCase().replaceAll(" ", "_"),
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.advancedutilities"
        );
    }

    public Module(String name, String description, Category category, boolean hold) {
        this(name, description, category);
        this.hold = hold;
    }

    public boolean isHold() { return hold; }

    /**
     * Registers a setting with this module.
     * Settings are automatically saved/loaded with the module's configuration.
     *
     * @param setting The setting to register
     */
    protected void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    /**
     * Gets all settings registered with this module.
     *
     * @return A list of all settings
     */
    public List<Setting<?>> getSettings() {
        return settings;
    }

    /**
     * Toggles the enabled state of this module.
     */
    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) onEnable();
        else onDisable();
        
        if (mc.player != null) {
            net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.literal(this.name + " ")
                    .withStyle(net.minecraft.ChatFormatting.WHITE)
                    .append(net.minecraft.network.chat.Component.literal(this.enabled ? "Enabled" : "Disabled")
                            .withStyle(this.enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
            mc.gui.setOverlayMessage(message, false);
        }
    }

    /**
     * Sets the enabled state of this module.
     *
     * @param enabled Whether the module should be enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public KeyMapping getKeyMapping() { return keyMapping; }

    /**
     * Called when the module is enabled.
     * Override this to perform initialization logic.
     */
    public void onEnable() {}

    /**
     * Called when the module is disabled.
     * Override this to perform cleanup logic.
     */
    public void onDisable() {}

    /**
     * Called every client tick when the module is enabled.
     * Override this to implement module functionality.
     */
    public void onTick() {}
}
