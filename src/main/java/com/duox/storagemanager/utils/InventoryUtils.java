package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/* Container and block interaction helpers. */
public class InventoryUtils {
    // The player inventory is always the last 36 slots of a container menu.
    public static final int PLAYER_INVENTORY_SIZE = 36;

    private static final Minecraft mc = Minecraft.getInstance();

    /* --- block-entity classification --- */

    public static boolean isStorable(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    public static boolean isDoubleChestLeftHalf(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.LEFT;
    }

    /* --- block interaction --- */

    /** Uses the item on a block without opening a screen. */
    public static void useItemOnSilently(BlockPos pos) {
        if (mc.player == null) return;

        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, pos, false);

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /* --- container clicks --- */

    public static void sendClickPacket(AbstractContainerMenu menu, int slotId, int button, ContainerInput clickType) {
        if (mc.player == null || mc.gameMode == null)
            return;

        // GameMode handles the packet format and state id.
        mc.gameMode.handleContainerInput(
                menu.containerId,
                slotId,
                button,
                clickType,
                mc.player
        );
    }

    public static int findEmptyPlayerSlot(AbstractContainerMenu menu, int containerSlotsEnd) {
        for (int i = containerSlotsEnd; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void closeContainerSilent() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    public static void quickMove(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 0, ContainerInput.QUICK_MOVE);
    }

    public static void pickup(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 0, ContainerInput.PICKUP);
    }

    public static void dropOne(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 1, ContainerInput.PICKUP);
    }

    /** Returns how many items can fit in the player's inventory. */
    public static int calculatePlayerSpace(AbstractContainerMenu menu, ItemStack stackToFit) {
        int freeSpace = 0;
        int containerSlotsEnd = menu.slots.size() - PLAYER_INVENTORY_SIZE;
        int maxStack = stackToFit.getMaxStackSize();

        for (int i = containerSlotsEnd; i < menu.slots.size(); i++) {
            ItemStack slotStack = menu.getSlot(i).getItem();
            if (slotStack.isEmpty()) {
                freeSpace += maxStack;
            } else if (ItemStack.isSameItemSameComponents(slotStack, stackToFit)) {
                freeSpace += Math.max(0, maxStack - slotStack.getCount());
            }
        }
        return freeSpace;
    }
}
