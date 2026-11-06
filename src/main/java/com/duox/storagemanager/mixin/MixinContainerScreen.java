package com.duox.storagemanager.mixin;

import com.duox.storagemanager.gui.StorageDragController;
import com.duox.storagemanager.gui.StoragePanel;
import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreen extends Screen {

    @Shadow
    private boolean skipNextRelease;

    @Shadow
    protected boolean isQuickCrafting;

    @Shadow
    protected Set<Slot> quickCraftSlots;

    @Unique
    private StoragePanel storagePanel;

    @Unique
    private StorageDragController storageDragController;

    // An overlay mouse-down must also own its matching mouse-up. Otherwise
    // vanilla may treat the release as a click on a slot behind the panel.
    @Unique
    private final Set<Integer> storageOverlayPressButtons = new HashSet<>();

    protected MixinContainerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        // Show the panel in chest and shulker screens while StorageManager is enabled.
        if (sm != null && sm.isEnabled()) {
            storagePanel = new StoragePanel(sm, sm.panelX.getInt(), sm.panelY.getInt());
            storageDragController = new StorageDragController();
            storagePanel.setDragController(storageDragController);
            this.addRenderableWidget(storagePanel);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onContainerClose(CallbackInfo ci) {
        clearPanelPendingRequests();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void onRemoved(CallbackInfo ci) {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (StorageTransferCoordinator.consumeRestoredScreenRemoval((Screen) (Object) this)
                || sm != null && sm.isRestoringCraftingScreen()) {
            if (storageDragController != null) storageDragController.clear();
            storageDragController = null;
            storagePanel = null;
        } else {
            clearPanelPendingRequests();
        }
    }

    @Unique
    private void clearPanelPendingRequests() {
        storageOverlayPressButtons.clear();
        if (storagePanel == null) {
            return;
        }

        // The player left this container. Do not run queued drag transfers after
        // its UI has closed (silent opens do not remove the original screen).
        StorageTransferCoordinator.clear();
        if (storageDragController != null) storageDragController.clear();
        storageDragController = null;

        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (sm != null) {
            sm.clearPendingRequests();
        }
        storagePanel = null;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (hasStaleMenu()) {
            if (storagePanel.isMouseOver(event.x(), event.y())) {
                storagePanel.mouseClicked(event, doubleClick);
                this.setFocused(storagePanel);
            } else if (!doubleClick && storageDragController != null) {
                if (storageDragController.beginStashClick(event,
                        (AbstractContainerScreen<?>) (Object) this, storagePanel, true)) {
                    cir.setReturnValue(true);
                    return;
                }
                storageDragController.beginInventory(event,
                        (AbstractContainerScreen<?>) (Object) this, storagePanel, true);
            }
            cir.setReturnValue(true);
            return;
        }
        // The storage overlay is rendered above the other screen widgets.
        // Give it first chance at input too, even when recipe-book widgets overlap it.
        if (storagePanel != null && storagePanel.isMouseOver(event.x(), event.y())
                && storagePanel.mouseClicked(event, doubleClick)) {
            storageOverlayPressButtons.add(event.button());
            this.setFocused(storagePanel);
            cir.setReturnValue(true);
            return;
        }
        if (!doubleClick && storagePanel != null && storageDragController != null) {
            if (storageDragController.beginStashClick(event,
                    (AbstractContainerScreen<?>) (Object) this, storagePanel, false)) {
                cir.setReturnValue(true);
                return;
            }
            storageDragController.beginInventory(event,
                    (AbstractContainerScreen<?>) (Object) this, storagePanel, false);
        }
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void afterMouseClicked(MouseButtonEvent event, boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (storageDragController != null) {
            storageDragController.confirmInventoryPickup((AbstractContainerScreen<?>) (Object) this);
        }
    }

    @Inject(method = "extractCarriedItem", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (storagePanel != null && storageDragController != null) {
            storageDragController.render(graphics,
                    (AbstractContainerScreen<?>) (Object) this, storagePanel, mouseX, mouseY);
        }
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
        if (hasStaleMenu()) {
            if (storagePanel != null && storageDragController != null) {
                storageDragController.mouseDragged(event, storagePanel,
                        (AbstractContainerScreen<?>) (Object) this);
                if (storagePanel.isHandlingDrag()) storagePanel.mouseDragged(event, dragX, dragY);
            }
            cir.setReturnValue(true);
            return;
        }
        if (storagePanel != null && storageDragController != null
                && storageDragController.mouseDragged(event, storagePanel,
                        (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }
        // Container slot dragging must not steal an active panel or scrollbar drag.
        if (storagePanel != null && storagePanel.isHandlingDrag()
                && storagePanel.mouseDragged(event, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (hasStaleMenu()) {
            if (storagePanel != null && storageDragController != null) {
                storageDragController.mouseReleased(event,
                        (AbstractContainerScreen<?>) (Object) this, storagePanel);
                storagePanel.mouseReleased(event);
            }
            resetVanillaDragState();
            cir.setReturnValue(true);
            return;
        }
        if (storagePanel != null && storageDragController != null
                && storageDragController.mouseReleased(event,
                        (AbstractContainerScreen<?>) (Object) this, storagePanel)) {
            storageOverlayPressButtons.remove(event.button());
            resetVanillaDragState();
            cir.setReturnValue(true);
            return;
        }
        if (storagePanel != null && storagePanel.isHandlingDrag()
                && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            storageOverlayPressButtons.remove(event.button());
            resetVanillaDragState();
            storagePanel.mouseReleased(event);
            cir.setReturnValue(true);
            return;
        }
        if (storagePanel != null && storageOverlayPressButtons.remove(event.button())) {
            storagePanel.mouseReleased(event);
            resetVanillaDragState();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void resetVanillaDragState() {
        this.setDragging(false);
        this.skipNextRelease = false;
        this.isQuickCrafting = false;
        this.quickCraftSlots.clear();
    }

    @Unique
    private boolean hasStaleMenu() {
        return storagePanel != null && Minecraft.getInstance().player != null
                && ((AbstractContainerScreen<?>) (Object) this).getMenu()
                        != Minecraft.getInstance().player.containerMenu;
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
