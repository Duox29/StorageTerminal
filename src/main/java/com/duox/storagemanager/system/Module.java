package com.duox.storagemanager.system;

import com.duox.storagemanager.system.settings.Setting;
import com.duox.storagemanager.StorageManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/* Base class for toggleable modules. */
public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private boolean enabled = false;
    private final KeyMapping keyMapping;

    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description) {
        this.name = name;
        this.description = description;

        this.keyMapping = new KeyMapping(
                name,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                StorageManager.KEY_CATEGORY
        );
    }

    protected void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public KeyMapping getKeyMapping() { return keyMapping; }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick() {}

    public void onWorldChanged() {}
}
