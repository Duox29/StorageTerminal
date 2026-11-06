package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/* Tracks containers opened manually by the player. */
public class ManualContainerTracker {
    private static BlockPos lastInteractedBlock = null;
    private static boolean isManualOpen = false;
    private static BlockPos pendingManualUpdate = null;
    private static int manualUpdateTimer = 0;
    private static BlockPos confirmedManualContainer = null;
    private static int confirmedMenuId = -1;

    public static void setLastInteractedBlock(BlockPos pos) {
        lastInteractedBlock = pos;
        isManualOpen = true;
        confirmedManualContainer = null;
        confirmedMenuId = -1;
    }

    public static BlockPos getLastInteractedBlock() {
        return lastInteractedBlock;
    }

    /** Prevent a pending manual cache read from sampling an automated container. */
    public static void cancelPendingManualUpdate() {
        isManualOpen = false;
        pendingManualUpdate = null;
        manualUpdateTimer = 0;
    }

    public static void onManualContainerOpen(BlockPos containerPos, Minecraft mc,
                                              int menuId, MenuType<?> menuType) {
        if (!isManualOpen) return;
        isManualOpen = false;
        if (mc.level == null || !InventoryUtils.matchesStorageMenu(containerPos, menuType)) return;

        BlockEntity be = mc.level.getBlockEntity(containerPos);
        if (!InventoryUtils.isStorable(be)) return;

        // Normalize a double chest to its right half.
        BlockPos actualPos = containerPos;
        if (be instanceof ChestBlockEntity) {
            BlockState state = be.getBlockState();
            if (InventoryUtils.isDoubleChestLeftHalf(state)) {
                Direction facing = state.getValue(ChestBlock.FACING);
                actualPos = containerPos.relative(facing.getClockWise());
            }
        }

        pendingManualUpdate = actualPos;
        manualUpdateTimer = 5; // Wait for the menu to sync.
        confirmedManualContainer = actualPos.immutable();
        confirmedMenuId = menuId;
    }

    /** Reopen only the chest belonging to the currently displayed manual menu. */
    public static BlockPos getConfirmedManualContainer(int menuId) {
        return menuId == confirmedMenuId ? confirmedManualContainer : null;
    }

    public static void tickManualCacheUpdate() {
        if (pendingManualUpdate == null) return;

        manualUpdateTimer--;
        if (manualUpdateTimer > 0) return;

        BlockPos pos = pendingManualUpdate;
        pendingManualUpdate = null;
        isManualOpen = false;

        // Verify the container is still open.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu != null
                && mc.player.containerMenu.containerId == confirmedMenuId
                && mc.player.containerMenu != mc.player.inventoryMenu) {
            ChestCache.updateFromContainerMenu(pos, mc.player.containerMenu, mc);
        }
    }

    public static void reset() {
        lastInteractedBlock = null;
        isManualOpen = false;
        pendingManualUpdate = null;
        manualUpdateTimer = 0;
        confirmedManualContainer = null;
        confirmedMenuId = -1;
    }
}
