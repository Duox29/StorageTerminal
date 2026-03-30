package com.duox.storagemanager.logic;

import com.duox.storagemanager.utils.ItemSerializer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

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

            String itemId = ItemSerializer.serialize(stack);
            String baseItemId = ItemSerializer.getBaseId(itemId);
            int count = stack.getCount();

            logger.accept("Slot " + i + ": " + itemId + " x" + count, "");

            // Debug logic: Cũng check theo Base ID thay vì ID NBT tuyệt đối
            boolean inAnyCache = cache.values().stream()
                    .anyMatch(contents -> contents.keySet().stream()
                            .anyMatch(k -> ItemSerializer.getBaseId(k).equals(baseItemId)));

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