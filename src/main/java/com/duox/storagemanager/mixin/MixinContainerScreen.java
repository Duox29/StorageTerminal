package com.duox.storagemanager.mixin;

import com.duox.storagemanager.gui.StoragePanel;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
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
        // Panel này sẽ xuất hiện trong Rương/Shulker nếu Module StorageManager đang
        // ENABLED.
        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            this.addRenderableWidget(storagePanel);
        }
    }

//    // FIX: Updated signature to match Minecraft 1.21+
//    // Screen.mouseDragged(MouseButtonEvent, double, double)
//    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
//    private void onMouseDragged(MouseButtonEvent event, double dragX, double dragY,
//            CallbackInfoReturnable<Boolean> cir) {
//        // Pass the event object directly to storagePanel
//        if (storagePanel != null && storagePanel.mouseDragged(event, dragX, dragY)) {
//            cir.setReturnValue(true);
//        }
//    }

    // Inject mouseScrolled to forward scroll events to StoragePanel
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.isSearchFocused()) {
            // Vẫn cho phép phím ESCAPE để thoát GUI bất cứ lúc nào
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                return;
            }

            // Ép gửi event cho StoragePanel xử lý (Backspace, Mũi tên trái/phải, v.v.)
            storagePanel.keyPressed(event);

            // Hủy event để nó không lan tới logic đóng Inventory của ContainerScreen
            cir.setReturnValue(true);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }
}