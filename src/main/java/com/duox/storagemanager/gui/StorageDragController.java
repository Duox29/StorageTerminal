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

/** Separates a panel gesture from vanilla inventory clicks. */
public final class StorageDragController {
    private static final double DRAG_THRESHOLD_SQUARED = 25.0;
    private static final int VALID_HIGHLIGHT = 0xFF55DD77;
    private static final int INVALID_HIGHLIGHT = 0xFFDD5555;

    private enum Source { NONE, PANEL, INVENTORY }

    private final Minecraft mc = Minecraft.getInstance();
    private Source source = Source.NONE;
    private ItemGridView.ItemSelection panelItem;
    private ItemGridView panelGrid;
    private int inventorySlot = -1;
    private int menuSlot = -1;
    private ItemStack inventoryStack = ItemStack.EMPTY;
    private AbstractContainerMenu sourceMenu;
    private boolean virtualInventoryDrag;
    private double pressX;
    private double pressY;
    private boolean dragging;

    public void beginPanel(ItemGridView grid, ItemGridView.ItemSelection item, double x, double y) {
        clear();
        source = Source.PANEL;
        StorageTransferCoordinator.setGestureHeld(true);
        panelGrid = grid;
        panelItem = item;
        pressX = x;
        pressY = y;
    }

    /**
     * Ctrl+click deposit. Queues the stack straight from its slot and reports
     * whether vanilla must skip its own click, so the item never leaves the slot
     * and rapid consecutive clicks are always accepted.
     */
    public boolean beginStashClick(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                                   StoragePanel panel, boolean virtual) {
        if (!event.hasControlDown()) return false;
        Slot slot = playerSlotPress(event, screen, panel, virtual, false);
        if (slot == null) return false;
        clear();
        StorageTransferCoordinator.stash(slot.getContainerSlot(), slot.getItem().copy());
        return true;
    }

    /** Observe vanilla's mouse-down; a stale screen uses a virtual drag. */
    public void beginInventory(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                               StoragePanel panel, boolean virtual) {
        clear();
        if (event.hasControlDown()) return;
        Slot slot = playerSlotPress(event, screen, panel, virtual, true);
        if (slot == null) return;

        source = Source.INVENTORY;
        StorageTransferCoordinator.setGestureHeld(true);
        sourceMenu = screen.getMenu();
        virtualInventoryDrag = virtual;
        inventorySlot = slot.getContainerSlot();
        menuSlot = slot.index;
        inventoryStack = slot.getItem().copy();
        pressX = event.x();
        pressY = event.y();
    }

    /**
     * Shared press test for a left-click on a player inventory slot outside the
     * panel. Returns the slot, or null when vanilla modifiers or the cursor own it.
     */
    private Slot playerSlotPress(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                                 StoragePanel panel, boolean virtual, boolean rejectBusyCursor) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || mc.player == null
                || event.hasShiftDown() || event.hasAltDown()
                || (event.modifiers() & InputConstants.MOD_SUPER) != 0
                || panel.isMouseOver(event.x(), event.y())
                || screen.getChildAt(event.x(), event.y()).isPresent()
                || (!virtual && screen.getMenu() != mc.player.containerMenu)
                || (rejectBusyCursor && !virtual && !mc.player.containerMenu.getCarried().isEmpty())) {
            return null;
        }

        Slot slot = playerSlotAt(screen, event.x(), event.y());
        if (slot == null || !slot.hasItem() || !slot.mayPickup(mc.player)) return null;
        ItemStack current = mc.player.getInventory().getItem(slot.getContainerSlot());
        return sameStack(current, slot.getItem()) ? slot : null;
    }

    /** Vanilla or an overlay may consume the press; only track a real pickup. */
    public void confirmInventoryPickup(AbstractContainerScreen<?> screen) {
        if (source == Source.INVENTORY && !virtualInventoryDrag && !sourcePickupIntact(screen)) clear();
    }

    public boolean mouseDragged(MouseButtonEvent event, StoragePanel panel, AbstractContainerScreen<?> screen) {
        if (source == Source.NONE) {
            return panel.isMouseOver(event.x(), event.y()) && !screen.getMenu().getCarried().isEmpty();
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        updateDragPosition(event.x(), event.y());
        return source == Source.PANEL || virtualInventoryDrag || panel.isMouseOver(event.x(), event.y());
    }

    private void updateDragPosition(double x, double y) {
        double dx = x - pressX;
        double dy = y - pressY;
        dragging |= dx * dx + dy * dy >= DRAG_THRESHOLD_SQUARED;
    }

    /** Returns true when vanilla must not process this release. */
    public boolean mouseReleased(MouseButtonEvent event, AbstractContainerScreen<?> screen, StoragePanel panel) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return panel.isMouseOver(event.x(), event.y()) && !screen.getMenu().getCarried().isEmpty();
        }
        if (source == Source.NONE) {
            return panel.isMouseOver(event.x(), event.y()) && !screen.getMenu().getCarried().isEmpty();
        }
        updateDragPosition(event.x(), event.y());

        if (source == Source.PANEL) {
            if (mc.player != null && (screen.getMenu() != mc.player.containerMenu
                    || mc.player.containerMenu.getCarried().isEmpty())) {
                if (!dragging) {
                    panelGrid.applyClick(panelItem, InputConstants.MOUSE_BUTTON_LEFT, false);
                } else if (!panel.isMouseOver(event.x(), event.y())) {
                    Slot target = playerSlotAt(screen, event.x(), event.y());
                    if (target != null) {
                        StorageTransferCoordinator.retrieve(panelItem.id(),
                                Math.min(panelItem.stack().getMaxStackSize(), panelItem.totalCount()),
                                target.getContainerSlot(), panelItem.stack());
                    }
                }
            }
        } else if (source == Source.INVENTORY) {
            if (virtualInventoryDrag) {
                if (dragging && panel.isStorageDropTarget(event.x(), event.y())
                        && mc.player != null
                        && sameStack(mc.player.getInventory().getItem(inventorySlot), inventoryStack)) {
                    StorageTransferCoordinator.stash(inventorySlot, inventoryStack);
                }
            } else if (!panel.isMouseOver(event.x(), event.y())) {
                clear();
                return false;
            } else if (restoreSourceStack(screen)) {
                if (dragging && panel.isStorageDropTarget(event.x(), event.y()))
                    StorageTransferCoordinator.stash(inventorySlot, inventoryStack);
            } else {
                ToastUtils.sendToast("§6Storage Terminal", "Drag cancelled; item remains on the cursor.");
            }
        }
        clear();
        return true;
    }

    private boolean sourcePickupIntact(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        return mc.player != null && menu == sourceMenu && menu == mc.player.containerMenu
                && menuSlot >= 0 && menuSlot < menu.slots.size()
                && menu.getSlot(menuSlot).getItem().isEmpty()
                && sameStack(menu.getCarried(), inventoryStack);
    }

    private boolean restoreSourceStack(AbstractContainerScreen<?> screen) {
        if (!sourcePickupIntact(screen)) return false;
        AbstractContainerMenu menu = screen.getMenu();
        InventoryUtils.sendClickPacket(menu, menuSlot, 0, ContainerInput.PICKUP);
        return menu.getCarried().isEmpty()
                && sameStack(menu.getSlot(menuSlot).getItem(), inventoryStack);
    }

    private Slot destinationSlot(AbstractContainerScreen<?> screen, double x, double y) {
        Slot hovered = playerSlotAt(screen, x, y);
        if (hovered == null) return null;
        if (!hovered.hasItem() && hovered.mayPlace(panelItem.stack())
                && mc.player.getInventory().getItem(hovered.getContainerSlot()).isEmpty())
            return hovered;
        int empty = InventoryUtils.findEmptyPlayerSlot(screen.getMenu(), panelItem.stack());
        return empty < 0 ? null : screen.getMenu().getSlot(empty);
    }

    public void render(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                       StoragePanel panel, int mouseX, int mouseY) {
        if (!dragging) return;
        if (source == Source.PANEL) {
            Slot hovered = panel.isMouseOver(mouseX, mouseY) ? null : playerSlotAt(screen, mouseX, mouseY);
            Slot destination = hovered == null ? null : destinationSlot(screen, mouseX, mouseY);
            Slot target = destination == null ? hovered : destination;
            if (target != null) {
                graphics.outline(screen.getLeftPos() + target.x - 1, screen.getTopPos() + target.y - 1,
                        18, 18, destination != null && InventoryUtils.getSlotSpace(destination, panelItem.stack()) > 0
                                ? VALID_HIGHLIGHT : INVALID_HIGHLIGHT);
            }
        } else if (source == Source.INVENTORY && panel.isStorageDropTarget(mouseX, mouseY)) {
            graphics.outline(panel.x, panel.y, panel.getPanelWidth(), panel.getPanelHeight(), VALID_HIGHLIGHT);
        }
        if (source == Source.PANEL || virtualInventoryDrag) {
            ItemStack preview = source == Source.PANEL ? panelItem.stack() : inventoryStack;
            graphics.nextStratum();
            graphics.item(preview, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(mc.font, preview, mouseX - 8, mouseY - 8, null);
        }
    }

    private Slot playerSlotAt(AbstractContainerScreen<?> screen, double x, double y) {
        Slot slot = slotAt(screen, x, y);
        return slot != null && mc.player != null && slot.container == mc.player.getInventory()
                && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < InventoryUtils.PLAYER_INVENTORY_SIZE
                ? slot : null;
    }

    private Slot slotAt(AbstractContainerScreen<?> screen, double x, double y) {
        double localX = x - screen.getLeftPos();
        double localY = y - screen.getTopPos();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive() && localX >= slot.x - 1 && localX < slot.x + 17
                    && localY >= slot.y - 1 && localY < slot.y + 17) return slot;
        }
        return null;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return !a.isEmpty() && ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    public void clear() {
        StorageTransferCoordinator.setGestureHeld(false);
        source = Source.NONE;
        panelItem = null;
        panelGrid = null;
        inventorySlot = -1;
        menuSlot = -1;
        inventoryStack = ItemStack.EMPTY;
        sourceMenu = null;
        virtualInventoryDrag = false;
        dragging = false;
    }
}
