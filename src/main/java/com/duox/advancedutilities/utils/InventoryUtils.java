package com.duox.advancedutilities.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class InventoryUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * Sends a click packet to the server.
     */
    public static void sendClickPacket(AbstractContainerMenu menu, int slotId, int button, ClickType clickType) {
        if (mc.player == null) return;

        ItemStack stack = ItemStack.EMPTY;
        if (slotId >= 0 && slotId < menu.slots.size()) {
             stack = menu.getSlot(slotId).getItem().copy();
        }

        mc.player.connection.send(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), slotId, button, clickType,
                stack,
                new Int2ObjectOpenHashMap<>()
        ));
    }

    /**
     * Finds the first empty slot in the player's inventory section of the container.
     * @param menu The container menu
     * @param containerSlotsEnd The index where the container slots end (and player inventory begins)
     * @return The slot index, or -1 if full
     */
    public static int findEmptyPlayerSlot(AbstractContainerMenu menu, int containerSlotsEnd) {
        for (int i = containerSlotsEnd; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Closes the current container silently.
     */
    public static void closeContainerSilent() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    /**
     * Moves items using Quick Move (Shift + Click).
     */
    public static void quickMove(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 0, ClickType.QUICK_MOVE);
    }

    /**
     * Pickups items (Left Click).
     */
    public static void pickup(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 0, ClickType.PICKUP);
    }

    /**
     * Drops one item (Right Click).
     */
    public static void dropOne(AbstractContainerMenu menu, int slotId) {
        sendClickPacket(menu, slotId, 1, ClickType.PICKUP);
    }
}
