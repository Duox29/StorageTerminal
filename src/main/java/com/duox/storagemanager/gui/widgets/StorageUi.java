package com.duox.storagemanager.gui.widgets;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ItemFilterCategory;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.BooleanSupplier;
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
        }).setIcon("hotbar").setActiveSupplier(() -> {
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
        }).setIcon("dump");
    }

    public static ModernButton requestButton(int x, int y, int width, int height, Runnable action) {
        return new ModernButton(x, y, width, height, Component.literal("Request"), button -> {
            action.run();
            playClick();
        }).setIcon("request");
    }

    public static ModernButton autoStashButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("Deposit"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null && !stash.isEnabled() && !stash.isCancelling()) {
                stash.setEnabled(true);
                playClick();
            }
        }).setIcon("deposit");
    }

    public static ModernButton clearCacheButton(int x, int y, int width, int height, Runnable onCleared) {
        return new ModernButton(x, y, width, height, Component.literal("Clear"), button -> {
            ChestCache.clearAll(Minecraft.getInstance());
            onCleared.run();
            ToastUtils.sendToast("§6Storage", "Cache cleared.");
            playClick();
        }).setIcon("clear");
    }

    public static ModernButton buildCacheButton(int x, int y, int width, int height) {
        return new ModernButton(x, y, width, height, Component.literal("Build"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null && !stash.isEnabled() && !stash.isCancelling()) {
                stash.requestCacheRebuild();
                stash.setEnabled(true);
                ToastUtils.sendToast("§bStorage", "Rebuilding cache...");
            }
            playClick();
        }).setIcon("build");
    }

    /* Filter category button; highlights while its category is the grid's current one. */
    public static ModernButton categoryButton(int x, int y, int width, int height, ItemFilterCategory category,
                                              Runnable onSelect, BooleanSupplier isActive) {
        ModernButton button = new ModernButton(x, y, width, height, Component.literal(category.displayName), pressed -> {
            onSelect.run();
            playClick();
        }).setActiveSupplier(isActive);
        return switch (category) {
            case BLOCKS -> button.setIcon("block", 300);
            case ORES -> button.setIcon("resource", 32);
            case TOOLS_ARMOR -> button.setIcon("gear", 32);
            case MISC -> button.setIcon("misc", 32);
            case ALL -> button.setIcon("all");
        };
    }
}
