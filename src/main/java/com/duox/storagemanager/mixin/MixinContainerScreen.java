package com.duox.storagemanager.mixin;

import com.duox.storagemanager.gui.StoragePanel;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
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

        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            this.addRenderableWidget(storagePanel);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (storagePanel != null && storagePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.isSearchFocused()) {
            // Vẫn cho phép phím ESCAPE để thoát GUI bất cứ lúc nào
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return;
            }

            // Ép gửi event cho StoragePanel xử lý (Backspace, Mũi tên trái/phải, v.v.)
            storagePanel.keyPressed(keyCode, scanCode, modifiers);

            // Hủy event để nó không lan tới logic đóng Inventory của ContainerScreen
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }
    @Override
    public void removed() {
        super.removed();
    }
}