package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.function.Consumer;

public class ManualContainerTracker {
    private static BlockPos lastInteractedBlock = null;
    private static boolean isManualOpen = false;
    private static BlockPos pendingManualUpdate = null;
    private static int manualUpdateTimer = 0;

    public static void setLastInteractedBlock(BlockPos pos) {
        lastInteractedBlock = pos;
        isManualOpen = true;
    }

    public static BlockPos getLastInteractedBlock() {
        return lastInteractedBlock;
    }

    public static void onManualContainerOpen(int containerId, BlockPos containerPos, Minecraft mc,
                                             Consumer<BlockPos> onUpdateReady) {
        if (!isManualOpen) return;

        BlockEntity be = mc.level.getBlockEntity(containerPos);
        if (!isValidContainer(be)) return;

        scheduleManualCacheUpdate(containerPos, be);
    }

    private static void scheduleManualCacheUpdate(BlockPos pos, BlockEntity be) {
        // Handle double chest: skip if LEFT chest, use RIGHT chest position instead
        BlockPos actualPos = pos;
        if (be instanceof ChestBlockEntity) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
                Direction facing = state.getValue(ChestBlock.FACING);
                actualPos = pos.relative(facing.getClockWise());
            }
        }

        pendingManualUpdate = actualPos;
        manualUpdateTimer = 5; // Wait 5 ticks for container menu to sync
    }

    public static void tick(Minecraft mc, Consumer<BlockPos> onUpdateReady) {
        if (pendingManualUpdate == null) return;

        manualUpdateTimer--;
        if (manualUpdateTimer <= 0) {
            // Verify container is still open
            if (mc.player != null && mc.player.containerMenu != null
                    && mc.player.containerMenu != mc.player.inventoryMenu) {
                onUpdateReady.accept(pendingManualUpdate);
            }
            pendingManualUpdate = null;
            isManualOpen = false;
        }
    }

    public static void tickManualCacheUpdate() { // Static hook for AutoStash
        Minecraft mc = Minecraft.getInstance();
        tick(mc, (pos) -> {
            if (mc.player != null) {
                ChestCache.updateFromContainerMenu(pos, mc.player.containerMenu, mc);
            }
        });
    }

    public static void reset() {
        lastInteractedBlock = null;
        isManualOpen = false;
        pendingManualUpdate = null;
        manualUpdateTimer = 0;
    }

    private static boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
    }
}