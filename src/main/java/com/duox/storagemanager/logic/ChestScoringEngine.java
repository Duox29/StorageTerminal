package com.duox.storagemanager.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.function.BiConsumer;

public class ChestScoringEngine {
    private final Level level;
    private final double distanceWeight;
    private final double concentrationWeight;
    private final double spaceWeight;

    public ChestScoringEngine(Level level, double distWeight, double concWeight, double spaceWeight) {
        this.level = level;
        this.distanceWeight = distWeight;
        this.concentrationWeight = concWeight;
        this.spaceWeight = spaceWeight;
    }

    public BlockPos findBestChest(String itemId, BlockPos playerPos, double maxRange,
                                  Map<String, Map<String, Integer>> cache,
                                  Set<BlockPos> processedChests,
                                  BiConsumer<String, String> debugLogger) {
        BlockPos bestPos = null;
        double maxScore = -1;
        double rangeSq = maxRange * maxRange;

        debugLogger.accept("findBestChest called for: ", itemId);
        debugLogger.accept("Player pos: " + playerPos + ", Range: " + maxRange, "");

        int checked = 0, skippedRange = 0, skippedProcessed = 0, skippedNoItem = 0, skippedNegative = 0;

        for (Map.Entry<String, Map<String, Integer>> entry : cache.entrySet()) {
            String posStr = entry.getKey();
            Map<String, Integer> contents = entry.getValue();
            BlockPos pos = com.duox.storagemanager.utils.CacheUtils.stringToPos(posStr);
            checked++;

            if (checked % 5 == 0) {
                debugLogger.accept("  Processed " + checked + "/" + cache.size() + " chests...", "");
            }

            double distSq = pos.distSqr(playerPos);
            if (distSq > rangeSq) {
                skippedRange++;
                continue;
            }

            if (processedChests.contains(pos)) {
                skippedProcessed++;
                continue;
            }

            if (!contents.containsKey(itemId)) {
                skippedNoItem++;
                continue;
            }

            double score = calculateScore(pos, contents, itemId, playerPos, debugLogger);

            if (score > maxScore) {
                maxScore = score;
                bestPos = pos;
                debugLogger.accept("  -> New best chest at " + posStr + " (score: " + score + ")", "");
            } else if (score < 0) {
                skippedNegative++;
            }
        }

        debugLogger.accept("Summary: checked=" + checked + " rangeSkip=" + skippedRange +
                " processedSkip=" + skippedProcessed + " noItemSkip=" + skippedNoItem +
                " negativeSkip=" + skippedNegative, "");

        return bestPos;
    }

    public double calculateScore(BlockPos pos, Map<String, Integer> contents, String itemId,
                                 BlockPos playerPos, BiConsumer<String, String> logger) {
        // Distance factor (closer = better)
        double distance = Math.sqrt(pos.distSqr(playerPos));
        double distScore = 1.0 / (1.0 + distance / 10.0);

        int maxStackSize = getMaxStackSize(itemId);
        int itemCount = contents.getOrDefault(itemId, 0);
        double itemScore = Math.min(1.0, itemCount / 64.0);

        ChestSpaceInfo spaceInfo = calculateSpace(pos, contents);
        logger.accept("  Chest " + pos + ": dist=" + String.format("%.2f", distance) +
                " items=" + itemCount + " availableSlots=" + spaceInfo.availableSlots, "");

        double spaceScore;
        if (spaceInfo.availableSlots == 0) {
            if (contents.containsKey(itemId)) {
                int lastStackCount = itemCount % maxStackSize;
                if (lastStackCount == 0) {
                    logger.accept("    All stacks FULL, excluding", "");
                    return -1.0;
                } else {
                    spaceScore = 0.3;
                    logger.accept("    Last stack has space, spaceScore=0.3", "");
                }
            } else {
                logger.accept("    No empty slots and item not present, excluding", "");
                return -1.0;
            }
        } else {
            int totalCapacity = spaceInfo.totalSlots * 64;
            int usedCapacity = contents.values().stream().mapToInt(Integer::intValue).sum();
            spaceScore = Math.max(0, 1.0 - (double) usedCapacity / totalCapacity);
        }

        double totalWeight = distanceWeight + concentrationWeight + spaceWeight;
        if (totalWeight == 0) totalWeight = 1.0;

        double finalScore = (distScore * distanceWeight + itemScore * concentrationWeight + spaceScore * spaceWeight) / totalWeight;

        logger.accept("    distScore=" + String.format("%.2f", distScore) + " itemScore=" + String.format("%.2f", itemScore) +
                " spaceScore=" + String.format("%.2f", spaceScore) + " final=" + String.format("%.2f", finalScore), "");

        return finalScore;
    }

    public ChestSpaceInfo calculateSpace(BlockPos pos, Map<String, Integer> contents) {
        BlockEntity be = level.getBlockEntity(pos);
        int totalSlots = getChestCapacity(be);

        int occupiedSlots = 0;
        boolean hasStackableSpace = false;

        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            String itemId = entry.getKey();
            int totalCount = entry.getValue();
            int maxStack = getMaxStackSize(itemId);

            int slotsTaken = (totalCount + maxStack - 1) / maxStack;
            occupiedSlots += slotsTaken;

            if (totalCount % maxStack != 0) {
                hasStackableSpace = true;
            }
        }

        int availableSlots = Math.max(0, totalSlots - occupiedSlots);
        return new ChestSpaceInfo(totalSlots, occupiedSlots, availableSlots, hasStackableSpace);
    }

    public static int getMaxStackSize(String itemId) {
        try {
            net.minecraft.resources.Identifier location = net.minecraft.resources.Identifier.parse(itemId);
            var optionalItem = BuiltInRegistries.ITEM.get(location);
            if (optionalItem.isPresent()) {
                return optionalItem.get().value().getDefaultMaxStackSize();
            }
        } catch (Exception e) {
            // Default to 64 on error
        }
        return 64;
    }

    public static int getChestCapacity(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            var state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    return 54; // Double chest
                }
            }
            return 27;
        } else if (be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
            return 27;
        }
        return 27;
    }

    public static class ChestSpaceInfo {
        public final int totalSlots;
        public final int usedSlots;
        public final int availableSlots;
        public final boolean hasStackableSpace;

        public ChestSpaceInfo(int totalSlots, int usedSlots, int availableSlots, boolean hasStackableSpace) {
            this.totalSlots = totalSlots;
            this.usedSlots = usedSlots;
            this.availableSlots = availableSlots;
            this.hasStackableSpace = hasStackableSpace;
        }
    }
}