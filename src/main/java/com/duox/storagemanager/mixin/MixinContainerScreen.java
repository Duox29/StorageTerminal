package com.duox.storagemanager.mixin;

import com.duox.storagemanager.gui.StoragePanel;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
        // Show the panel in chest and shulker screens while StorageManager is enabled.
        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            this.addRenderableWidget(storagePanel);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onContainerClose(CallbackInfo ci) {
        clearPanelPendingRequests();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void onRemoved(CallbackInfo ci) {
        clearPanelPendingRequests();
    }

    @Unique
    private void clearPanelPendingRequests() {
        if (storagePanel == null) {
            return;
        }

        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (sm != null) {
            sm.clearPendingRequests();
        }
        storagePanel = null;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent event, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        // Container slot dragging must not steal an active panel or scrollbar drag.
        if (storagePanel != null && storagePanel.isHandlingDrag()
                && storagePanel.mouseDragged(event, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.isHandlingDrag()
                && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.setDragging(false);
            storagePanel.mouseReleased(event);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (storagePanel != null && storagePanel.isSearchFocused()) {
            // Let Escape close the screen.
            if (event.key() == InputConstants.KEY_ESCAPE) {
                return;
            }

            // Forward typing and navigation keys to the search box.
            storagePanel.keyPressed(event);
            cir.setReturnValue(true);
        }
    }

}
