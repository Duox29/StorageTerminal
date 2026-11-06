package com.duox.storagemanager.logic;

import com.duox.storagemanager.utils.ItemSerializer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StashPlanner {
    private final ChestScoringEngine scoringEngine;

    public StashPlanner(ChestScoringEngine scoringEngine) {
        this.scoringEngine = scoringEngine;
    }

    public Map<BlockPos, List<Integer>> calculatePlan(LocalPlayer player, boolean includeHotbar, double range,
                                                      Map<String, Map<String, Integer>> cache,
                                                      Set<BlockPos> processedChests,
                                                      boolean forceDump) {
        Map<BlockPos, List<Integer>> plan = new LinkedHashMap<>();

        if (player == null) {
            return plan;
        }

        int startInv = includeHotbar ? 0 : 9;

        for (int i = startInv; i < 36; i++) {
            addInventorySlot(plan, player, i, range, cache, processedChests, forceDump);
        }

        return plan;
    }

    public Map<BlockPos, List<Integer>> calculatePlanForSlot(LocalPlayer player, int inventorySlot,
                                                               double range, Map<String, Map<String, Integer>> cache,
                                                               Set<BlockPos> processedChests, boolean forceDump) {
        Map<BlockPos, List<Integer>> plan = new LinkedHashMap<>();
        if (player != null && inventorySlot >= 0 && inventorySlot < 36) {
            addInventorySlot(plan, player, inventorySlot, range, cache, processedChests, forceDump);
        }
        return plan;
    }

    private void addInventorySlot(Map<BlockPos, List<Integer>> plan, LocalPlayer player,
                                  int inventorySlot, double range, Map<String, Map<String, Integer>> cache,
                                  Set<BlockPos> processedChests, boolean forceDump) {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty()) return;

        String itemId = ItemSerializer.serialize(stack);
        BlockPos bestChest = scoringEngine.findBestChest(itemId, player.blockPosition(), range,
                cache, processedChests, forceDump);
        if (bestChest != null) {
            plan.computeIfAbsent(bestChest, k -> new ArrayList<>()).add(inventorySlot);
        }
    }
}
