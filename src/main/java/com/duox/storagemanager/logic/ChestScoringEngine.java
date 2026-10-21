package com.duox.storagemanager.logic;

import com.duox.storagemanager.utils.CacheUtils;
import com.duox.storagemanager.utils.ItemSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/* Scores candidate chests for stashing. */
public class ChestScoringEngine {
    /* Stack sizes are registry constants; memoize the parsed id lookups. */
    private static final Map<String, Integer> MAX_STACK_SIZES = new HashMap<>();

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
                                  boolean forceDump) {
        BlockPos bestPos = null;
        double maxScore = -1;
        double rangeSq = maxRange * maxRange;

        for (Map.Entry<String, Map<String, Integer>> entry : cache.entrySet()) {
            String posStr = entry.getKey();
            Map<String, Integer> contents = entry.getValue();
            BlockPos pos = CacheUtils.stringToPos(posStr);

            double distSq = pos.distSqr(playerPos);
            if (distSq > rangeSq) {
                continue;
            }

            if (processedChests.contains(pos)) {
                continue;
            }

            double score = scoreChest(pos, contents, itemId, playerPos, forceDump);

            if (score > maxScore) {
                maxScore = score;
                bestPos = pos;
            }
        }

        return bestPos;
    }

    /* Scores one chest; matching, concentration, and space share a single pass. */
    private double scoreChest(BlockPos pos, Map<String, Integer> contents, String itemId,
                              BlockPos playerPos, boolean forceDump) {
        double distScore = 1.0 / (1.0 + Math.sqrt(pos.distSqr(playerPos)) / 10.0);

        // Match containers by base id, ignoring item components.
        String baseItemId = ItemSerializer.getBaseId(itemId);
        boolean hasExactMatch = false;
        int baseItemCount = 0;
        int occupiedSlots = 0;

        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            boolean exactMatch = entry.getKey().equals(itemId);
            if (exactMatch) {
                hasExactMatch = true;
                baseItemCount += entry.getValue();
            } else if (ItemSerializer.getBaseId(entry.getKey()).equals(baseItemId)) {
                baseItemCount += entry.getValue();
            }
            int maxStack = getMaxStackSize(entry.getKey());
            occupiedSlots += (entry.getValue() + maxStack - 1) / maxStack;
        }

        // Normal stashing only uses chests that already hold this item type.
        if (!forceDump && baseItemCount == 0) {
            return -1.0;
        }

        int totalSlots = getChestCapacity(level.getBlockEntity(pos));
        int availableSlots = Math.max(0, totalSlots - occupiedSlots);

        double itemScore = Math.min(1.0, baseItemCount / 64.0);

        double spaceScore;
        if (availableSlots == 0) {
            // A full chest can only accept a partially filled matching stack.
            if (!hasExactMatch) {
                return -1.0; // Different components cannot merge.
            }
            int lastStackCount = contents.get(itemId) % getMaxStackSize(itemId);
            if (lastStackCount == 0) {
                return -1.0; // All stacks are full.
            }
            spaceScore = 0.3; // Room in the last stack.
        } else {
            int totalCapacity = totalSlots * 64;
            int usedCapacity = contents.values().stream().mapToInt(Integer::intValue).sum();
            spaceScore = Math.max(0, 1.0 - (double) usedCapacity / totalCapacity);
        }

        double totalWeight = distanceWeight + concentrationWeight + spaceWeight;
        if (totalWeight == 0) totalWeight = 1.0;

        return (distScore * distanceWeight + itemScore * concentrationWeight + spaceScore * spaceWeight) / totalWeight;
    }

    private static int getMaxStackSize(String itemId) {
        Integer cached = MAX_STACK_SIZES.get(itemId);
        if (cached != null) return cached;

        int size = 64;
        try {
            String baseId = ItemSerializer.getBaseId(itemId);
            Identifier location = Identifier.parse(baseId);
            Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
            if (item != null) {
                size = item.getDefaultMaxStackSize();
            }
        } catch (Exception e) {
            // Use the vanilla default on failure.
        }
        MAX_STACK_SIZES.put(itemId, size);
        return size;
    }

    private static int getChestCapacity(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            var state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    return 54; // Double chest.
                }
            }
            return 27;
        } else if (be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
            return 27;
        }
        return 27;
    }
}
