package com.duox.storagemanager.logic;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.InventoryUtils;
import com.duox.storagemanager.utils.ItemSerializer;
import com.duox.storagemanager.utils.ManualContainerTracker;
import com.duox.storagemanager.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Runs drag transfers one at a time; both modules share the silent container connection. */
public final class StorageTransferCoordinator {
    private record Transfer(String itemId, int quantity, int inventorySlot, boolean stash,
                            ItemStack expectedStack) {}
    private record ReturnContext(Screen screen, ClientLevel level, BlockPos pos) {}

    private static final Deque<Transfer> pending = new ArrayDeque<>();
    private static Transfer active;
    private static ReturnContext returnContext;
    private static Screen restoringScreen;
    private static int restoringScreenTimeout;

    private StorageTransferCoordinator() {}

    public static void retrieve(String itemId, int quantity, int inventorySlot, ItemStack expectedStack) {
        if (quantity > 0 && inventorySlot >= 0 && inventorySlot < 36
                && expectedStack != null && !expectedStack.isEmpty()) {
            pending.addLast(new Transfer(itemId, quantity, inventorySlot, false, expectedStack.copy()));
        }
    }

    public static boolean stash(int inventorySlot, ItemStack expectedStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || inventorySlot < 0 || inventorySlot >= 36
                || expectedStack == null || expectedStack.isEmpty()) return false;
        ItemStack current = mc.player.getInventory().getItem(inventorySlot);
        if (!ItemStack.isSameItemSameComponents(current, expectedStack)
                || current.getCount() != expectedStack.getCount()) {
            ToastUtils.sendToast("§6Storage Terminal", "Drag cancelled: inventory slot changed.");
            return false;
        }
        // A second gesture cannot reserve the same physical stack twice. The
        // slot may be used again after the first operation finishes.
        if ((active != null && active.stash() && active.inventorySlot() == inventorySlot)
                || pending.stream().anyMatch(t -> t.stash() && t.inventorySlot() == inventorySlot)) {
            ToastUtils.sendToast("§6Storage Terminal", "That inventory slot is already queued.");
            return false;
        }
        pending.addLast(new Transfer(ItemSerializer.serialize(expectedStack), expectedStack.getCount(),
                inventorySlot, true, expectedStack.copy()));
        return true;
    }

    public static void clear() {
        pending.clear();
        active = null;
        returnContext = null;
        restoringScreen = null;
        restoringScreenTimeout = 0;
    }

    /** Ordinary retrieval must wait until the complete drag batch and GUI restoration finish. */
    public static boolean hasPendingTransfers() {
        return active != null || !pending.isEmpty() || returnContext != null || restoringScreen != null;
    }

    /** The old GUI is being replaced with the same manually opened chest. */
    public static boolean consumeRestoredScreenRemoval(Screen screen) {
        if (restoringScreen != screen) return false;
        restoringScreen = null;
        restoringScreenTimeout = 0;
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (restoringScreen != null && --restoringScreenTimeout <= 0) {
            Screen stale = restoringScreen;
            restoringScreen = null;
            if (mc.gui.screen() == stale && mc.player.containerMenu == mc.player.inventoryMenu) {
                closeStaleScreen(mc);
            }
        }
        // Wait for the server to finish replacing the previous manual screen.
        // A new silent open here could race with that screen's container ID.
        if (restoringScreen != null) return;
        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (active != null) {
            if (storage != null && storage.isRetrieving() || autoStash != null && autoStash.isEnabled()) return;
            active = null;
        }
        if (pending.isEmpty()) {
            // A carried stack must not strand the previous container screen.
            // Pending transfers still wait for an empty cursor below, but once
            // the batch is finished the original GUI can safely be reopened.
            reopenPreviousContainer();
            return;
        }
        if (!mc.player.containerMenu.getCarried().isEmpty()) return;

        if (storage == null || !storage.isEnabled() || autoStash == null
                || storage.isRetrieving() || autoStash.isEnabled()) {
            return;
        }

        ReturnContext candidate = returnContext == null ? capturePreviousContainer() : null;
        Transfer next = pending.removeFirst();
        boolean started = next.stash()
                ? autoStash.startTargetedStash(next.inventorySlot(), next.expectedStack())
                : storage.startTargetedRetrieval(next.itemId(), next.quantity(),
                        next.inventorySlot(), next.expectedStack());
        if (started && returnContext == null) {
            returnContext = candidate;
        }
        if (started) {
            active = next;
        } else {
            ToastUtils.sendToast("§6Storage Terminal", "Drag transfer cancelled: items or slot changed.");
        }
    }

    private static ReturnContext capturePreviousContainer() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (mc.level == null || mc.player == null
                || !(screen instanceof AbstractContainerScreen<?> containerScreen)
                || containerScreen.getMenu() != mc.player.containerMenu
                || mc.player.containerMenu == mc.player.inventoryMenu) return null;

        BlockPos pos = null;
        if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
            BlockPos last = ManualContainerTracker.getConfirmedManualContainer(
                    mc.player.containerMenu.containerId, mc.level);
            if (last != null && InventoryUtils.isStorable(mc.level.getBlockEntity(last))) {
                pos = last.immutable();
            }
        }
        // For other container screens, remember to close the stale GUI once the
        // silent menu is closed. Only a manually opened chest can be reopened.
        return new ReturnContext(screen, mc.level, pos);
    }

    private static void reopenPreviousContainer() {
        if (returnContext == null) return;
        Minecraft mc = Minecraft.getInstance();
        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if ((storage != null && storage.isRetrieving()) || (stash != null && stash.isEnabled())) return;

        ReturnContext context = returnContext;
        returnContext = null;
        if (context.pos() != null && mc.player != null && mc.level == context.level()
                && mc.gui.screen() == context.screen()
                && mc.player.containerMenu == mc.player.inventoryMenu
                && InventoryUtils.isStorable(mc.level.getBlockEntity(context.pos()))) {
            // With silent mode off, the server's new screen replaces the stale chest GUI.
            // Mark this as a manual-screen restore so its cache is refreshed on open.
            restoringScreen = context.screen();
            restoringScreenTimeout = 40;
            InventoryUtils.useItemOnSilently(context.pos(), true);
        } else if (mc.player != null && mc.gui.screen() == context.screen()
                && mc.player.containerMenu == mc.player.inventoryMenu) {
            closeStaleScreen(mc);
        }
    }

    private static void closeStaleScreen(Minecraft mc) {
        mc.gui.setScreen(null);
        ToastUtils.sendToast("§6Storage Terminal", "Could not reopen the previous container.");
    }
}
