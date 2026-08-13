package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.MenuType;
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
        } else if (autoStash != null) {
            // Nếu không phải silent mode, có thể là manual open
            // Gọi onManualContainerOpen với vị trí block đã lưu
            if (AutoStash.getLastInteractedBlock() != null) {
                autoStash.onManualContainerOpen(packet.getContainerId(), AutoStash.getLastInteractedBlock());
            }
        }

        StorageManager storageManager = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (storageManager != null && storageManager.isEnabled()) {
            if (packet.getType() == MenuType.CRAFTING) {
                // Server-confirmed crafting table: signal recipe placement readiness.
                storageManager.onCraftingContainerOpened(packet.getContainerId());
            } else if (storageManager.isSilentMode()) {
                storageManager.onSilentContainerOpen(packet.getContainerId(), packet.getType());
            }
        }
    }
}
