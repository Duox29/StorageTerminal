package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.modules.AutoStash;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
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
public class MixinMinecraft {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof AbstractContainerScreen<?>) {
            AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (autoStash != null && autoStash.isEnabled() && autoStash.isSilentMode()) {
                if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
                    ci.cancel();
                    return;
                }
                return;
            }

            StorageManager storageManager = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (storageManager != null && storageManager.isEnabled() && storageManager.isSilentMode()) {
                if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
                    ci.cancel();
                    return;
                }
                return;
            }
        }
    }
}
