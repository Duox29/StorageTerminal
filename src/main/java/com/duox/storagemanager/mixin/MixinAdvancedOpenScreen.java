package com.duox.storagemanager.mixin;

import com.duox.storagemanager.utils.ContainerOpenTracker;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.neoforged.neoforge.client.network.ClientPayloadHandler", remap = false)
public class MixinAdvancedOpenScreen {
    @Inject(method = "handle(Lnet/neoforged/neoforge/network/payload/AdvancedOpenScreenPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V", at = @At("HEAD"))
    private static void storageManager$onOpen(AdvancedOpenScreenPayload packet, IPayloadContext context, CallbackInfo ci) {
        ContainerOpenTracker.onOpen(packet.windowId(), packet.menuType());
    }
}
