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
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.block.CraftingTableBlock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Runs drag transfers one at a time; both modules share the silent container connection. */
public final class StorageTransferCoordinator {
    private record Transfer(String itemId, int quantity, int inventorySlot, boolean stash,
                            ItemStack expectedStack, Map<String, Integer> requests,
                            RecipeDisplayId recipe, boolean placeAll) {}
    private record ReturnContext(Screen screen, BlockPos pos) {}

    private static final Deque<Transfer> pending = new ArrayDeque<>();
    private static ReturnContext returnContext;
    private static Screen restoringScreen;
    private static Screen deferredScreen;
    private static int restoringScreenTimeout;
    private static boolean gestureHeld;
    private static Transfer active;

    private StorageTransferCoordinator() {}

    public static void retrieve(String itemId, int quantity, int inventorySlot, ItemStack expectedStack) {
        pending.addLast(new Transfer(itemId, quantity, inventorySlot, false, expectedStack.copy(), null, null, false));
    }

    public static void retrieveRequests(Map<String, Integer> requests, RecipeDisplayId recipe, boolean placeAll) {
        pending.addLast(new Transfer(null, 0, -1, false, ItemStack.EMPTY, Map.copyOf(requests), recipe, placeAll));
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
        if (sameStash(active, inventorySlot, expectedStack)
                || pending.stream().anyMatch(transfer -> sameStash(transfer, inventorySlot, expectedStack))) {
            ToastUtils.sendToast("§6Storage Terminal", "This stack is already queued.");
            return true;
        }
        pending.addLast(new Transfer(ItemSerializer.serialize(expectedStack), expectedStack.getCount(),
                inventorySlot, true, expectedStack.copy(), null, null, false));
        return true;
    }

    public static void clear() {
        if (active != null && active.requests() == null) {
            StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (storage != null) storage.cancelTargetedRetrieval();
            if (stash != null) stash.cancelTargetedStash();
        }
        pending.clear();
        returnContext = null;
        restoringScreen = null;
        deferredScreen = null;
        restoringScreenTimeout = 0;
        gestureHeld = false;
        active = null;
    }

    /** A module toggle cancels the batch but keeps the visible screen's return context. */
    public static void cancelForDisable() {
        ReturnContext context = returnContext;
        clear();
        returnContext = context;
    }

    private static boolean sameStash(Transfer transfer, int slot, ItemStack stack) {
        return transfer != null && transfer.stash() && transfer.inventorySlot() == slot
                && transfer.expectedStack().getCount() == stack.getCount()
                && ItemStack.isSameItemSameComponents(transfer.expectedStack(), stack);
    }

    public static void setGestureHeld(boolean held) {
        gestureHeld = held;
    }

    public static int queuedCount() {
        return pending.size();
    }

    /** Keep the old screen alive until a gesture started during restoration ends. */
    public static boolean deferRestoredScreen(Screen screen) {
        if (restoringScreen == null || !gestureHeld || deferredScreen != null) return false;
        deferredScreen = screen;
        return true;
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
        if (deferredScreen != null && !gestureHeld) {
            Screen screen = deferredScreen;
            deferredScreen = null;
            mc.gui.setScreen(screen);
        }
        if (restoringScreen != null && !gestureHeld && --restoringScreenTimeout <= 0) {
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
        if (active != null && (storage == null || !storage.isRetrieving())
                && (autoStash == null || !autoStash.isEnabled() && !autoStash.isCancelling())) active = null;
        if (pending.isEmpty()) {
            // A carried stack must not strand the previous container screen.
            // Pending transfers still wait for an empty cursor below, but once
            // the batch is finished the original GUI can safely be reopened.
            if (!gestureHeld) reopenPreviousContainer();
            return;
        }
        if (!mc.player.containerMenu.getCarried().isEmpty()) return;

        if (storage == null || !storage.isEnabled() || autoStash == null
                || storage.isRetrieving() || autoStash.isEnabled() || autoStash.isCancelling()) {
            return;
        }

        Transfer next = pending.removeFirst();
        ReturnContext candidate = returnContext == null && next.recipe() == null
                ? capturePreviousContainer() : null;
        boolean started = next.requests() != null
                ? storage.startQueuedRetrieval(next.requests(), next.recipe(), next.placeAll())
                : next.stash()
                ? autoStash.startTargetedStash(next.inventorySlot(), next.expectedStack())
                : storage.startTargetedRetrieval(next.itemId(), next.quantity(),
                        next.inventorySlot(), next.expectedStack());
        if (started) {
            active = next;
            if (returnContext == null) returnContext = candidate;
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

        BlockPos pos = ManualContainerTracker.getConfirmedManualContainer(
                mc.player.containerMenu.containerId);
        if (pos != null && !canReopen(screen, pos)) pos = null;
        // For other container screens, remember to close the stale GUI once the
        // silent menu is closed. Only a manually opened chest can be reopened.
        return new ReturnContext(screen, pos);
    }

    private static void reopenPreviousContainer() {
        if (returnContext == null) return;
        Minecraft mc = Minecraft.getInstance();
        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if ((storage != null && storage.isRetrieving())
                || (stash != null && (stash.isEnabled() || stash.isCancelling()))) return;

        ReturnContext context = returnContext;
        returnContext = null;
        if (context.pos() != null && mc.player != null
                && mc.gui.screen() == context.screen()
                && mc.player.containerMenu == mc.player.inventoryMenu
                && canReopen(context.screen(), context.pos())) {
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

    private static boolean canReopen(Screen screen, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return screen instanceof CraftingScreen
                ? mc.level.getBlockState(pos).getBlock() instanceof CraftingTableBlock
                : InventoryUtils.isStorable(mc.level.getBlockEntity(pos));
    }
}
