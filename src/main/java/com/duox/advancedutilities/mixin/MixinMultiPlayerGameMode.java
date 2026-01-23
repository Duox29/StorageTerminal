package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.modules.AutoStash;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        // Lưu vị trí block mà người chơi đang click vào
        // Điều này sẽ được sử dụng để xác định xem container được mở có phải do manual click không
        AutoStash.setLastInteractedBlock(hitResult.getBlockPos());
    }
}
