package com.duox.storagemanager.mixin;

import com.duox.storagemanager.gui.StoragePanel;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        // Panel này sẽ xuất hiện trong Rương/Shulker nếu Module StorageManager đang ENABLED.
        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            this.addRenderableWidget(storagePanel);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 1. Logic Custom của bạn (tương tự phần @At HEAD)
        // Kiểm tra xem storagePanel có xử lý sự kiện này không
        if (this.storagePanel != null && this.storagePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true; // Tương đương cir.setReturnValue(true) - chặn sự kiện lan tiếp
        }

        // 2. Gọi logic của class cha (Screen)
        // Điều này QUAN TRỌNG để giữ các tính năng mặc định khác của GUI (nếu có) vẫn hoạt động
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        super.removed();
    }
}
