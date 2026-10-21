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
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = ItemSerializer.serialize(stack);

            BlockPos bestChest = scoringEngine.findBestChest(itemId, player.blockPosition(), range,
                    cache, processedChests, forceDump);

            if (bestChest != null) {
                plan.computeIfAbsent(bestChest, k -> new ArrayList<>()).add(i);
            }
        }

        return plan;
    }
}
