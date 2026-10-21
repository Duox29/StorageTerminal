package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ManualContainerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "handleOpenScreen", at = @At("HEAD"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null) {
            if (autoStash.isEnabled()) {
                autoStash.onSilentContainerOpen(packet.getContainerId(), packet.getType());
            } else {
                // Treat the open as manual if a block was clicked.
                BlockPos last = ManualContainerTracker.getLastInteractedBlock();
                if (last != null) {
                    ManualContainerTracker.onManualContainerOpen(last, Minecraft.getInstance());
                }
            }
        }

        StorageManager storageManager = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (storageManager != null && storageManager.isSilentMode()) {
            storageManager.onSilentContainerOpen(packet.getContainerId(), packet.getType());
        }
    }
}
