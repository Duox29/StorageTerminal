package com.duox.storagemanager.gui.widgets;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/* Shared button and sound helpers. */
public final class StorageUi {
    private StorageUi() {}

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static Component toggleLabel(String name, boolean on) {
        return Component.literal(name)
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public static Component hotbarLabel() {
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        return toggleLabel("Hotbar", stash != null && stash.isIncludeHotbar());
    }

    public static ModernButton hotbarButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, hotbarLabel(), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.toggleIncludeHotbar();
                button.setMessage(hotbarLabel());
            }
            playClick();
        }).setActiveSupplier(() -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            return stash != null && stash.isIncludeHotbar();
        });
    }

    public static ModernButton requestButton(int x, int y, int width, int height, Runnable action) {
        return new ModernButton(x, y, width, height, Component.literal("Request"), button -> {
            action.run();
            playClick();
        });
    }

    public static ModernButton autoStashButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("AutoStash"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.setEnabled(true);
                playClick();
            }
        });
    }
}
