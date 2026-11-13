package com.duox.storagemanager.utils;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;

/** Routes vanilla and NeoForge screen-open notifications to the active operation. */
public final class ContainerOpenTracker {
    private ContainerOpenTracker() {}

    public static void onOpen(int containerId, MenuType<?> menuType) {
        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (storage != null && storage.isSilentMode()) {
            storage.onSilentContainerOpen(containerId, menuType);
            return;
        }
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (stash != null && stash.isSilentMode()) {
            stash.onSilentContainerOpen(containerId, menuType);
            return;
        }
        BlockPos pos = ManualContainerTracker.getLastInteractedBlock();
        if (pos != null) {
            ManualContainerTracker.onManualContainerOpen(pos, Minecraft.getInstance(), containerId, menuType);
        }
    }
}
