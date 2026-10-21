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
        // Show the panel in chest and shulker screens while StorageManager is enabled.
        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            this.addRenderableWidget(storagePanel);
        }
    }

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
            // Let Escape close the screen.
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                return;
            }

            // Forward typing and navigation keys to the search box.
            storagePanel.keyPressed(event);
            cir.setReturnValue(true);
        }
    }

}