package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.modules.AutoStash;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
        if (autoStash != null && autoStash.isEnabled() && autoStash.isSilentMode()) {
            autoStash.onSilentContainerOpen(packet.getContainerId(), packet.getType());
        }

        StorageManager storageManager = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (storageManager != null && storageManager.isEnabled() && storageManager.isSilentMode()) {
            storageManager.onSilentContainerOpen(packet.getContainerId(), packet.getType());
        }
    }
}
