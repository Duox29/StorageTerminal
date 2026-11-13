package com.duox.storagemanager.mixin;

import com.duox.storagemanager.compat.SophisticatedStorageCompat;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinGui {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (StorageTransferCoordinator.deferRestoredScreen(screen)) {
            ci.cancel();
            return;
        }

        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        StorageManager storageManager = ModuleManager.INSTANCE.getModule(StorageManager.class);

        boolean suppressContainer = (autoStash != null && autoStash.isSilentMode())
                || (storageManager != null && storageManager.isSilentMode());

        if (suppressContainer && (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen
                || SophisticatedStorageCompat.isStorageMenu(containerScreen.getMenu()))) {
            ci.cancel();
        }
    }
}
