package com.duox.storagemanager.gui.widgets;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

/* Shared button and sound helpers. */
public final class StorageUi {
    private static final int SEARCH_MAX_LENGTH = 50;

    private StorageUi() {}

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /* Search field shared by the in-game panel and the full screen. */
    public static EditBox searchBox(int x, int y, int width, int height, Consumer<String> responder) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, height,
                Component.literal("Search"));
        box.setMaxLength(SEARCH_MAX_LENGTH);
        box.setTextColor(0xFFFFFFFF);
        box.setResponder(responder);
        return box;
    }

    public static ModernButton hotbarButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("Hotbar"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.toggleIncludeHotbar();
            }
            playClick();
        }).setActiveSupplier(() -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            return stash != null && stash.isIncludeHotbar();
        });
    }

    public static ModernButton dumpButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("Dump"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.startDumpAll();
                ToastUtils.sendToast("§6Storage", "Dumping inventory...");
            }
            playClick();
        });
    }

    public static ModernButton requestButton(int x, int y, int width, int height, Runnable action) {
        return new ModernButton(x, y, width, height, Component.literal("Request"), button -> {
            action.run();
            playClick();
        });
    }

    public static ModernButton autoStashButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("Deposit"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null && !stash.isEnabled() && !stash.isCancelling()) {
                stash.setEnabled(true);
                playClick();
            }
        });
    }
}
