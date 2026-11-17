package com.duox.storagemanager.utils;

import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.modules.StorageManager;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** Builds ingredient requests shared by recipe sources. */
public final class RecipeIngredientRequests {
    private final Minecraft minecraft;

    public RecipeIngredientRequests(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void requestIngredientsFromEntry(RecipeDisplayEntry entry, RecipeDisplayId id,
                                             StorageManager sm, boolean placeAll) {
        Map<String, Integer> requests = plan(ingredientOptions(entry), placeAll);
        if (requests == null) {
            ToastUtils.sendToast("§cMissing Ingredients", "Not enough ingredients in inventory and storage.");
            return;
        }
        if (requests.isEmpty()) return;
        if (minecraft.player != null && minecraft.gui.screen() instanceof CraftingScreen screen
                && screen.getMenu() == minecraft.player.containerMenu) {
            var pos = ManualContainerTracker.getConfirmedManualContainer(screen.getMenu().containerId);
            if (pos != null) {
                boolean queued = StorageTransferCoordinator.retrieveRecipe(requests, pos, screen.getMenu().containerId, restored -> {
                    if (restored != null && minecraft.gameMode != null)
                        minecraft.gameMode.handlePlaceRecipe(restored.getMenu().containerId, id, placeAll);
                });
                if (!queued) ToastUtils.sendToast("§6Storage Terminal", "Recipe transfer is already in progress.");
                return;
            }
        }
        requests.forEach(sm::addToRequestQueue);
        sm.startRetrieval();
    }

    /** Returns missing ingredients, an empty map if already available, or null if unavailable. */
    public Map<String, Integer> plan(List<List<ItemStack>> options, boolean placeAll) {
        if (minecraft.player == null) return null;
        Map<String, Integer> playerInv = new HashMap<>();
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < InventoryUtils.PLAYER_INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            playerInv.merge(itemId, stack.getCount(), Integer::sum);
        }

        // Ingredients already in the crafting grid are also available for transfer.
        if (minecraft.gui.screen() instanceof CraftingScreen screen
                && screen.getMenu() == minecraft.player.containerMenu) {
            for (int i = 1; i <= 9; i++) {
                ItemStack stack = screen.getMenu().getSlot(i).getItem();
                if (stack.isEmpty()) continue;
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                playerInv.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        // Allocate the least flexible slots first so tag ingredients can use what's left.
        List<List<String>> slots = options.stream()
                .map(stacks -> stacks.stream().filter(stack -> !stack.isEmpty())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        .distinct().toList())
                .filter(ids -> !ids.isEmpty())
                .sorted(Comparator.comparingInt(List::size))
                .toList();
        if (slots.isEmpty()) return null;

        Map<String, Integer> storageCounts = new HashMap<>();
        Map<String, Integer> baseCost = new HashMap<>();
        for (List<String> alternatives : slots) {
            String bestId = null;
            int bestScore = -1;
            int bestTieBreak = -1;
            for (String itemId : alternatives) {
                int reserved = baseCost.getOrDefault(itemId, 0);
                int carried = playerInv.getOrDefault(itemId, 0);
                int total = carried + storageCounts.computeIfAbsent(itemId, this::getTotalInCache);
                int available = total - reserved;
                if (available <= 0) continue;

                int carriedRemaining = Math.max(0, carried - reserved);
                // For Shift-click, favor the variant that supports more complete sets.
                int score = placeAll ? total / (reserved + 1) : carriedRemaining;
                int tieBreak = placeAll ? carriedRemaining : available;
                if (score > bestScore || score == bestScore && tieBreak > bestTieBreak) {
                    bestId = itemId;
                    bestScore = score;
                    bestTieBreak = tieBreak;
                }
            }
            if (bestId == null) return null;
            baseCost.merge(bestId, 1, Integer::sum);
        }

        int craftCount = placeAll ? maxCraftCount(baseCost, playerInv, storageCounts) : 1;
        Map<String, Integer> requests = new HashMap<>();
        for (Map.Entry<String, Integer> cost : baseCost.entrySet()) {
            int needed = craftCount * cost.getValue() - playerInv.getOrDefault(cost.getKey(), 0);
            if (needed > 0) requests.put(cost.getKey(), needed);
        }
        return requests;
    }

    /** Largest craft count the cache plus inventory can afford. */
    private int maxCraftCount(Map<String, Integer> costPerCraft, Map<String, Integer> playerInv,
                              Map<String, Integer> storageCounts) {
        int max = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> cost : costPerCraft.entrySet()) {
            int available = storageCounts.get(cost.getKey()) + playerInv.getOrDefault(cost.getKey(), 0);
            // Vanilla caps each ingredient slot at one stack.
            max = Math.min(max, Math.min(available / cost.getValue(), 64));
        }
        return max;
    }

    /** Returns possible stacks for each ingredient slot. */
    private List<List<ItemStack>> ingredientOptions(RecipeDisplayEntry entry) {
        List<List<ItemStack>> options = new ArrayList<>();
        if (minecraft.level == null) return options;

        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);

        List<Ingredient> requirements = entry.craftingRequirements().orElse(List.of());
        if (!requirements.isEmpty()) {
            for (Ingredient ingredient : requirements) {
                options.add(ingredient.display().resolveForStacks(context));
            }
            return options;
        }

        for (SlotDisplay slot : ingredientSlots(entry.display())) {
            options.add(slot.resolveForStacks(context));
        }
        return options;
    }

    private List<SlotDisplay> ingredientSlots(RecipeDisplay display) {
        return switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
            case FurnaceRecipeDisplay furnace -> List.of(furnace.ingredient());
            case SmithingRecipeDisplay smithing ->
                    List.of(smithing.template(), smithing.base(), smithing.addition());
            case StonecutterRecipeDisplay stonecutter -> List.of(stonecutter.input());
            default -> List.of();
        };
    }

    /** Returns the total cached amount of an item. */
    private int getTotalInCache(String itemId) {
        int total = 0;
        for (Map<String, Integer> chestContents : ChestCache.getActiveCache().values()) {
            if (chestContents != null) {
                for (var entry : chestContents.entrySet()) {
                    if (ItemSerializer.getBaseId(entry.getKey()).equals(itemId)) total += entry.getValue();
                }
            }
        }
        return total;
    }
}
