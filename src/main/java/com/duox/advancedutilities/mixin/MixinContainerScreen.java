package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.gui.StoragePanel;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreen extends Screen {

    @Unique
    private StoragePanel storagePanel;

    protected MixinContainerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (sm != null && sm.isEnabled()) {

            // Read position from settings
            int x = sm.panelX.getInt();
            int y = sm.panelY.getInt();

            storagePanel = new StoragePanel(sm, x, y);

            // QUAN TRỌNG: Thêm Panel như một Widget chính thống
            // Việc này tự động kích hoạt render, click, scroll, tooltip cho Panel
            this.addRenderableWidget(storagePanel);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }
}
