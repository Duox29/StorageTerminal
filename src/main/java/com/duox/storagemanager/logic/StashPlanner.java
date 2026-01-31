package com.duox.storagemanager.logic;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.function.BiConsumer;

public class StashPlanner {
    private final ChestScoringEngine scoringEngine;
    private final BiConsumer<String, String> logger;

    public StashPlanner(ChestScoringEngine scoringEngine, BiConsumer<String, String> logger) {
        this.scoringEngine = scoringEngine;
        this.logger = logger;
    }

    public Map<BlockPos, List<Integer>> calculatePlan(LocalPlayer player, boolean includeHotbar, double range,
                                                      Map<String, Map<String, Integer>> cache,
                                                      Set<BlockPos> processedChests) {
        logger.accept("=== Starting calculateStashPlan ===", "");
        Map<BlockPos, List<Integer>> plan = new LinkedHashMap<>();

        if (player == null) {
            logger.accept("Player is null!", "");
            return plan;
        }

        int startInv = includeHotbar ? 0 : 9;
        logger.accept("Scanning inventory from slot " + startInv + " to 35", "");

        for (int i = startInv; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int count = stack.getCount();

            logger.accept("Slot " + i + ": " + itemId + " x" + count, "");

            // Check if item exists in any cache for debugging
            boolean inAnyCache = cache.values().stream()
                    .anyMatch(contents -> contents.containsKey(itemId));

            if (!inAnyCache) {
                logger.accept("  Item NOT found in any active cache!", "");
            }

            BlockPos bestChest = scoringEngine.findBestChest(itemId, player.blockPosition(), range,
                    cache, processedChests, logger);

            if (bestChest != null) {
                logger.accept("  -> Assigned to chest at " + bestChest, "");
                plan.computeIfAbsent(bestChest, k -> new ArrayList<>()).add(i);
            } else {
                logger.accept("  -> findBestChest returned NULL!", "");
            }
        }

        logger.accept("=== Stash Plan Summary: Total chests in plan: " + plan.size() + " ===", "");
        return plan;
    }
}