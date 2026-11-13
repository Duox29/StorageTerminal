package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ContainerOpenTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Redirect(method = "handleContainerClose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
    private void preserveManualScreenWhileOpening(LocalPlayer player, ClientboundContainerClosePacket packet) {
        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        boolean opening = storage != null && storage.isWaitingForSilentOpen()
                || stash != null && stash.isWaitingForSilentOpen();
        if (opening && player.containerMenu != player.inventoryMenu
                && packet.getContainerId() == player.containerMenu.containerId
                && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>) {
            // Vanilla also closes the visible GUI here. The server is replacing
            // its menu for our silent open, so keep the old screen as an overlay.
            player.containerMenu = player.inventoryMenu;
        } else {
            player.clientSideCloseContainer();
        }
    }

    @Inject(method = "handleOpenScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        ContainerOpenTracker.onOpen(packet.getContainerId(), packet.getType());
    }
}
