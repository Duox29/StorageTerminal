package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class MixinPlayer {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void modifyBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash == null || autoStash.getRange() < 4.5) {
            return;
        }
        cir.setReturnValue(Math.max(cir.getReturnValue(), autoStash.getRange()));
    }
}
