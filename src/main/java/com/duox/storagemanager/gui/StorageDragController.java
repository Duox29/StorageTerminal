package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.ItemGridView;
import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.utils.InventoryUtils;
import com.duox.storagemanager.utils.ToastUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Separates a click from a drag without changing vanilla slot clicks. */
public final class StorageDragController {
    private static final double DRAG_THRESHOLD_SQUARED = 25.0;
    private static final int VALID_HIGHLIGHT = 0xFF55DD77;
    private static final int INVALID_HIGHLIGHT = 0xFFDD5555;

    private enum Source { NONE, PANEL, INVENTORY }

    private final Minecraft mc = Minecraft.getInstance();
    private Source source = Source.NONE;
    private ItemGridView.ItemSelection panelItem;
    private ItemGridView panelGrid;
    private int sourceMenuSlot = -1;
    private int sourceInventorySlot = -1;
    private ItemStack sourceStack = ItemStack.EMPTY;
    private AbstractContainerMenu sourceMenu;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private boolean virtualInventoryDrag;
    private int blockedCarriedPanelButton = -1;

    /** Prevent an overlay click with a vanilla cursor stack from dropping it on release. */
    public void blockCarriedPanelClick(int button) {
        clear();
        blockedCarriedPanelButton = button;
    }

    public void beginPanel(ItemGridView grid, ItemGridView.ItemSelection item, double x, double y) {
        clear();
        source = Source.PANEL;
        panelGrid = grid;
        panelItem = item;
        pressX = x;
        pressY = y;
    }

    public void beginInventory(MouseButtonEvent event, AbstractContainerScreen<?> screen, StoragePanel panel,
                               boolean virtual) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        clear();
        if (mc.player == null || event.hasShiftDown() || event.hasControlDown() || event.hasAltDown()
                || (event.modifiers() & InputConstants.MOD_SUPER) != 0
                || panel.isMouseOver(event.x(), event.y())
                || (!virtual && screen.getMenu() != mc.player.containerMenu)
                || !mc.player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        Slot slot = playerSlotAt(screen, event.x(), event.y());
        if (slot == null || !slot.hasItem() || !slot.mayPickup(mc.player)) return;

        source = Source.INVENTORY;
        virtualInventoryDrag = virtual;
        sourceMenu = screen.getMenu();
        sourceMenuSlot = slot.index;
        sourceInventorySlot = slot.getContainerSlot();
        sourceStack = slot.getItem().copy();
        pressX = event.x();
        pressY = event.y();
    }

    /** A recipe-book or other overlay widget may consume mouse-down before vanilla picks up a slot. */
    public void confirmInventoryPickup(AbstractContainerScreen<?> screen) {
        if (source != Source.INVENTORY || virtualInventoryDrag) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.player == null || menu != sourceMenu || menu != mc.player.containerMenu
                || sourceMenuSlot < 0 || sourceMenuSlot >= menu.slots.size()
                || !menu.getSlot(sourceMenuSlot).getItem().isEmpty()
                || !ItemStack.isSameItemSameComponents(menu.getCarried(), sourceStack)
                || menu.getCarried().getCount() != sourceStack.getCount()) {
            clear();
        }
    }

    public boolean mouseDragged(MouseButtonEvent event, StoragePanel panel,
                                AbstractContainerScreen<?> screen) {
        if (event.button() == blockedCarriedPanelButton) return true;
        // Vanilla quick-craft may distribute items into slots hidden by our overlay.
        // Let it handle every ordinary drag elsewhere in the container.
        if (source == Source.NONE) {
            return panel.isMouseOver(event.x(), event.y())
                    && !screen.getMenu().getCarried().isEmpty();
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        updateDragPosition(event.x(), event.y());
        return source == Source.PANEL || panel.isMouseOver(event.x(), event.y());
    }

    private void updateDragPosition(double x, double y) {
        if ((x - pressX) * (x - pressX) + (y - pressY) * (y - pressY) >= DRAG_THRESHOLD_SQUARED) {
            dragging = true;
        }
    }

    /** True when the release is ours and must not reach vanilla's outside-slot drop handling. */
    public boolean mouseReleased(MouseButtonEvent event, AbstractContainerScreen<?> screen, StoragePanel panel) {
        if (event.button() == blockedCarriedPanelButton) {
            clear();
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;

        if (source != Source.NONE) updateDragPosition(event.x(), event.y());

        if (source == Source.PANEL) {
            if (mc.player == null || screen.getMenu() != mc.player.containerMenu
                    && !StorageTransferCoordinator.hasPendingTransfers()) {
                clear();
                return true;
            }
            if (!dragging) {
                panelGrid.applyClick(panelItem, InputConstants.MOUSE_BUTTON_LEFT, false);
            } else {
                // The panel is rendered above the inventory. Never target a slot hidden beneath it.
                Slot target = panel.isMouseOver(event.x(), event.y())
                        ? null : playerSlotAt(screen, event.x(), event.y());
                int amount = target == null ? 0 : availableSpace(target, panelItem.stack());
                if (amount > 0) {
                    StorageTransferCoordinator.retrieve(panelItem.id(),
                            Math.min(amount, panelItem.totalCount()), target.getContainerSlot(), panelItem.stack());
                } else if (target != null) {
                    ToastUtils.sendToast("§6Storage Terminal", "Target slot cannot accept this item.");
                }
            }
            clear();
            return true;
        }

        if (source == Source.INVENTORY) {
            if (virtualInventoryDrag) {
                if (dragging && panel.isStorageDropTarget(event.x(), event.y())
                        && mc.player != null && mc.player.containerMenu.getCarried().isEmpty()) {
                    StorageTransferCoordinator.stash(sourceInventorySlot, sourceStack);
                }
                clear();
                return true;
            }
            if (!dragging) {
                // The vanilla mouse-down already picked up the stack. A short
                // movement onto our overlay is a cancelled drag, not a stash.
                // Put the stack back before consuming the mouse release.
                if (panel.isMouseOver(event.x(), event.y())) {
                    if (!restoreSourceStack(screen)) {
                        ToastUtils.sendToast("§6Storage Terminal", "Drag cancelled; item remains on the cursor.");
                    }
                    clear();
                    return true;
                }
                clear();
                return false;
            }

            boolean validTarget = panel.isStorageDropTarget(event.x(), event.y());
            if (!validTarget && !panel.isMouseOver(event.x(), event.y())
                    && insideVanillaContainer(screen, event.x(), event.y())) {
                // Keep normal container interactions intact. Vanilla owns the
                // carried stack when the gesture ends inside its own GUI.
                clear();
                return false;
            }

            // A vanilla PICKUP happened on mouse down. Restore the stack before
            // a targeted stash, or before cancelling a drag outside the GUI.
            if (restoreSourceStack(screen)) {
                if (validTarget) StorageTransferCoordinator.stash(sourceInventorySlot, sourceStack);
            } else {
                ToastUtils.sendToast("§6Storage Terminal", "Drag cancelled; item remains on the cursor.");
            }

            // Consume panel and outside releases so vanilla cannot drop the
            // carried item via the outside slot (-999).
            clear();
            return true;
        }

        clear();
        // A carried stack released over the panel must never be interpreted as a world drop.
        return panel.isMouseOver(event.x(), event.y()) && !screen.getMenu().getCarried().isEmpty();
    }

    private boolean restoreSourceStack(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.player == null || menu != sourceMenu || menu != mc.player.containerMenu
                || sourceMenuSlot < 0 || sourceMenuSlot >= menu.slots.size()
                || !menu.getSlot(sourceMenuSlot).getItem().isEmpty()
                || !ItemStack.isSameItemSameComponents(menu.getCarried(), sourceStack)
                || menu.getCarried().getCount() != sourceStack.getCount()) {
            return false;
        }
        InventoryUtils.sendClickPacket(menu, sourceMenuSlot, 0, ContainerInput.PICKUP);
        ItemStack restored = menu.getSlot(sourceMenuSlot).getItem();
        return menu.getCarried().isEmpty()
                && ItemStack.isSameItemSameComponents(restored, sourceStack)
                && restored.getCount() == sourceStack.getCount();
    }

    public void render(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                       StoragePanel panel, int mouseX, int mouseY) {
        if (!dragging) return;

        if (source == Source.PANEL && panelItem != null) {
            Slot target = panel.isMouseOver(mouseX, mouseY) ? null : playerSlotAt(screen, mouseX, mouseY);
            if (target != null) {
                int x = screen.getLeftPos() + target.x - 1;
                int y = screen.getTopPos() + target.y - 1;
                graphics.outline(x, y, 18, 18,
                        availableSpace(target, panelItem.stack()) > 0 ? VALID_HIGHLIGHT : INVALID_HIGHLIGHT);
            }
            graphics.nextStratum();
            graphics.item(panelItem.stack(), mouseX - 8, mouseY - 8);
            graphics.itemDecorations(mc.font, panelItem.stack(), mouseX - 8, mouseY - 8, null);
        } else if (source == Source.INVENTORY) {
            if (panel.isStorageDropTarget(mouseX, mouseY)) {
                graphics.outline(panel.x, panel.y, panel.getPanelWidth(), panel.getPanelHeight(), VALID_HIGHLIGHT);
            }
            if (virtualInventoryDrag) {
                graphics.nextStratum();
                graphics.item(sourceStack, mouseX - 8, mouseY - 8);
                graphics.itemDecorations(mc.font, sourceStack, mouseX - 8, mouseY - 8, null);
            }
        }
    }

    private Slot playerSlotAt(AbstractContainerScreen<?> screen, double x, double y) {
        if (mc.player == null) return null;
        double localX = x - screen.getLeftPos();
        double localY = y - screen.getTopPos();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive() && localX >= slot.x - 1 && localX < slot.x + 17
                    && localY >= slot.y - 1 && localY < slot.y + 17) {
                // Match the first visible vanilla slot. A chest slot can overlap
                // a player slot in modded menus and must take priority.
                if (slot.container != mc.player.getInventory()) return null;
                int inv = slot.getContainerSlot();
                return inv >= 0 && inv < 36 ? slot : null;
            }
        }
        return null;
    }

    private boolean insideVanillaContainer(AbstractContainerScreen<?> screen, double x, double y) {
        return x >= screen.getLeftPos() && x < screen.getLeftPos() + screen.getImageWidth()
                && y >= screen.getTopPos() && y < screen.getTopPos() + screen.getImageHeight();
    }

    private int availableSpace(Slot slot, ItemStack stack) {
        if (!slot.mayPlace(stack)) return 0;
        ItemStack current = slot.getItem();
        if (current.isEmpty()) return Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        return Math.max(0, Math.min(current.getMaxStackSize(), slot.getMaxStackSize(current)) - current.getCount());
    }

    public void clear() {
        blockedCarriedPanelButton = -1;
        source = Source.NONE;
        panelItem = null;
        panelGrid = null;
        sourceMenu = null;
        sourceMenuSlot = -1;
        sourceInventorySlot = -1;
        sourceStack = ItemStack.EMPTY;
        dragging = false;
        virtualInventoryDrag = false;
    }
}
