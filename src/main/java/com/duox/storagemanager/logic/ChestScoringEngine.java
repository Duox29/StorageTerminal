package com.duox.storagemanager.logic;

import com.duox.storagemanager.utils.ItemSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.Set;
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

        // GIẢI PHÁP: Trích xuất ID gốc (Bỏ qua NBT/Damage) để tìm rương
        String baseItemId = ItemSerializer.getBaseId(itemId);

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

            // KIỂM TRA MỞ RỘNG: Tìm xem rương có item nào chung Base ID không
            boolean foundBaseMatch = false;
            for (String chestKey : contents.keySet()) {
                if (ItemSerializer.getBaseId(chestKey).equals(baseItemId)) {
                    foundBaseMatch = true;
                    break;
                }
            }

            if (!foundBaseMatch) {
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
        double distance = Math.sqrt(pos.distSqr(playerPos));
        double distScore = 1.0 / (1.0 + distance / 10.0);

        int maxStackSize = getMaxStackSize(itemId);

        // Gộp điểm tập trung (Concentration) cho tất cả đồ nghề có cùng Base ID trong rương này
        int baseItemCount = 0;
        boolean hasExactMatch = false;
        String baseItemId = ItemSerializer.getBaseId(itemId);

        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            if (entry.getKey().equals(itemId)) {
                hasExactMatch = true;
                baseItemCount += entry.getValue();
            } else if (ItemSerializer.getBaseId(entry.getKey()).equals(baseItemId)) {
                baseItemCount += entry.getValue();
            }
        }

        double itemScore = Math.min(1.0, baseItemCount / 64.0);
        ChestSpaceInfo spaceInfo = calculateSpace(pos, contents);

        double spaceScore;
        if (spaceInfo.availableSlots == 0) {
            // Rương đầy, chỉ có thể cất nếu có slot dở (và NBT phải giống hệt nhau 100%)
            if (hasExactMatch) {
                int exactItemCount = contents.get(itemId);
                int lastStackCount = exactItemCount % maxStackSize;
                if (lastStackCount == 0) {
                    return -1.0; // Các stack đều đã đạt Max Stack -> Cấm
                } else {
                    spaceScore = 0.3; // Còn chỗ dở trên stack
                }
            } else {
                return -1.0; // Đồ có NBT khác sẽ ko nhét được vào stack cũ -> Cấm
            }
        } else {
            int totalCapacity = spaceInfo.totalSlots * 64;
            int usedCapacity = contents.values().stream().mapToInt(Integer::intValue).sum();
            spaceScore = Math.max(0, 1.0 - (double) usedCapacity / totalCapacity);
        }

        double totalWeight = distanceWeight + concentrationWeight + spaceWeight;
        if (totalWeight == 0) totalWeight = 1.0;

        return (distScore * distanceWeight + itemScore * concentrationWeight + spaceScore * spaceWeight) / totalWeight;
    }

    public ChestSpaceInfo calculateSpace(BlockPos pos, Map<String, Integer> contents) {
        BlockEntity be = level.getBlockEntity(pos);
        int totalSlots = getChestCapacity(be);

        int occupiedSlots = 0;
        boolean hasStackableSpace = false;

        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            String chestItemId = entry.getKey();
            int totalCount = entry.getValue();
            int maxStack = getMaxStackSize(chestItemId);

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
            // FIX BOM NỔ CHẬM: Phải lấy base ID trước khi Parse, nếu không Parser sẽ bị Crash NBT
            String baseId = ItemSerializer.getBaseId(itemId);
            net.minecraft.resources.Identifier location = net.minecraft.resources.Identifier.parse(baseId);
            Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
            if (item != null) {
                return item.getDefaultMaxStackSize();
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