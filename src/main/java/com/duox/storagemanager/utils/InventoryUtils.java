package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
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
    private static boolean silentBlockInteraction;

    public static boolean isSilentBlockInteraction() {
        return silentBlockInteraction;
    }

    /* --- block-entity classification --- */

    public static boolean isStorable(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    /** Rejects unrelated menus while an automated chest interaction is in flight. */
    public static boolean matchesStorageMenu(BlockPos pos, MenuType<?> menuType) {
        if (mc.level == null || pos == null) return false;
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof ShulkerBoxBlockEntity) return menuType == MenuType.SHULKER_BOX;
        if (be instanceof BarrelBlockEntity) return menuType == MenuType.GENERIC_9x3;
        if (be instanceof ChestBlockEntity) {
            return menuType == (be.getBlockState().getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6);
        }
        return false;
    }

    public static boolean isDoubleChestLeftHalf(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.LEFT;
    }

    /* --- block interaction --- */

    /** Uses the item on a block without opening a screen. */
    public static void useItemOnSilently(BlockPos pos) {
        useItemOnSilently(pos, false);
    }

    /** Restores a previously manual container when {@code restoreManualScreen} is true. */
    public static void useItemOnSilently(BlockPos pos, boolean restoreManualScreen) {
        if (mc.player == null || mc.gameMode == null) return;

        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, pos, false);

        // The game-mode mixin must only track player-initiated container opens.
        // Automated opens otherwise replace the manually selected chest position.
        ManualContainerTracker.cancelPendingManualUpdate();
        if (restoreManualScreen) ManualContainerTracker.setLastInteractedBlock(pos);
        boolean wasSilent = silentBlockInteraction;
        silentBlockInteraction = true;
        try {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        } finally {
            silentBlockInteraction = wasSilent;
        }
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

    public static int findEmptyPlayerSlot(AbstractContainerMenu menu, ItemStack stack) {
        if (mc.player == null) return -1;
        for (Slot slot : menu.slots) {
            int inventorySlot = slot.getContainerSlot();
            if (slot.container == mc.player.getInventory() && slot.isActive()
                    && inventorySlot >= 0 && inventorySlot < PLAYER_INVENTORY_SIZE
                    && !slot.hasItem() && mc.player.getInventory().getItem(inventorySlot).isEmpty()
                    && slot.mayPlace(stack)) return slot.index;
        }
        return -1;
    }

    /** First empty inventory index, searching the main inventory before the hotbar. */
    public static int findEmptyInventorySlot() {
        if (mc.player == null) return -1;
        Inventory inventory = mc.player.getInventory();
        for (int offset = 0; offset < PLAYER_INVENTORY_SIZE; offset++) {
            int i = (offset + 9) % PLAYER_INVENTORY_SIZE;
            if (inventory.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Maps the player's 0-35 inventory indices to slots in a chest-like menu. */
    public static int mapInventorySlotToMenuSlot(int inventorySlot, int containerSlots) {
        if (inventorySlot < 0 || inventorySlot >= PLAYER_INVENTORY_SIZE) return -1;
        return inventorySlot < 9 ? containerSlots + 27 + inventorySlot : containerSlots + inventorySlot - 9;
    }

    /** How many items of the given stack a slot can still accept. */
    public static int getSlotSpace(Slot slot, ItemStack stack) {
        if (!slot.mayPlace(stack)) return 0;
        ItemStack current = slot.getItem();
        if (current.isEmpty()) return Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        return Math.max(0, Math.min(current.getMaxStackSize(), slot.getMaxStackSize(current)) - current.getCount());
    }

    public static void closeContainerSilent() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    /**
     * Closes a silently opened container only while that exact menu is still the
     * active one, so a manually opened screen is never closed by mistake.
     */
    public static void closeSilentIfOpen(int silentContainerId) {
        if (mc.player != null && silentContainerId != -1
                && mc.player.containerMenu.containerId == silentContainerId) {
            closeContainerSilent();
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
