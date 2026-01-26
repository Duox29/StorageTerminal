package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Player.class)
public class MixinPlayer {

    @Overwrite
    public double blockInteractionRange() {
        // Ép kiểu 'this' về Player để gọi các hàm khác nếu cần
        Player player = (Player) (Object) this;

        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);

        if (autoStash == null) {
            return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        }
        if(autoStash.getRange() < 4.5) {
            return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        }
        return autoStash.getRange();
    }
}
