package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Requests missing recipe ingredients from storage. */
@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {
    private static final Logger LOGGER = LogManager.getLogger();

    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"))
    private void onTryPlaceRecipe(RecipeCollection collection, RecipeDisplayId id, boolean placeAll,
                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (sm == null || !sm.isEnabled() || !sm.autoRequestRecipe.getValue()) {
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;

            RecipeDisplayEntry entry = collection.getRecipes().stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst()
                    .orElse(null);
            if (entry != null) {
                requestIngredientsFromEntry(entry, id, sm, placeAll);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to request recipe ingredients", e);
        }
    }

    private void requestIngredientsFromEntry(RecipeDisplayEntry entry, RecipeDisplayId id,
                                             StorageManager sm, boolean placeAll) {
        // Build the ingredient cost for one craft.
        Map<String, Integer> baseCost = new HashMap<>();
        for (List<ItemStack> possibleItems : ingredientOptions(entry)) {
            if (possibleItems.isEmpty()) continue;

            String bestItemId = null;
            int maxAvailable = -1;

            // Prefer the variant with the most cached items.
            for (ItemStack stack : possibleItems) {
                if (stack.isEmpty()) continue;
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int available = getTotalInCache(itemId);
                if (available > maxAvailable) {
                    maxAvailable = available;
                    bestItemId = itemId;
                }
            }

            // Fall back to the first valid variant.
            if (bestItemId == null || maxAvailable <= 0) {
                bestItemId = BuiltInRegistries.ITEM.getKey(possibleItems.get(0).getItem()).toString();
            }

            baseCost.merge(bestItemId, 1, Integer::sum);
        }

        if (baseCost.isEmpty()) return;

        // Count matching items in the player's inventory.
        Map<String, Integer> playerInv = new HashMap<>();
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (baseCost.containsKey(itemId)) {
                playerInv.put(itemId, playerInv.getOrDefault(itemId, 0) + stack.getCount());
            }
        }

        int craftCount = placeAll ? maxCraftCount(baseCost, playerInv) : 1;

        if (craftCount <= 0) {
            // Not enough items for even one craft; show the missing ingredients.
            StringBuilder warning = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> cost : baseCost.entrySet()) {
                int available = getTotalInCache(cost.getKey()) + playerInv.getOrDefault(cost.getKey(), 0);
                if (available >= cost.getValue()) continue;
                if (!first) warning.append(", ");
                warning.append(cost.getValue() - available).append("x ")
                        .append(cost.getKey().replace("minecraft:", ""));
                first = false;
            }
            ToastUtils.sendToast("§cMissing Ingredients", warning.toString());
            return;
        }

        // Queue only the missing amount per ingredient.
        boolean addedAny = false;
        for (Map.Entry<String, Integer> cost : baseCost.entrySet()) {
            int needed = craftCount * cost.getValue();
            int inPlayer = playerInv.getOrDefault(cost.getKey(), 0);
            int toPull = needed - inPlayer;
            if (toPull > 0) {
                sm.addToRequestQueue(cost.getKey(), toPull);
                addedAny = true;
            }
        }

        if (addedAny) {
            // Place the recipe after retrieval finishes.
            sm.setPendingRecipe(id, placeAll);
            sm.startRetrieval();
        }
    }

    /** Largest craft count the cache plus inventory can afford. */
    private int maxCraftCount(Map<String, Integer> costPerCraft, Map<String, Integer> playerInv) {
        int max = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> cost : costPerCraft.entrySet()) {
            int available = getTotalInCache(cost.getKey()) + playerInv.getOrDefault(cost.getKey(), 0);
            // Vanilla caps each ingredient slot at one stack.
            max = Math.min(max, Math.min(available / cost.getValue(), 64 / cost.getValue()));
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
                total += chestContents.getOrDefault(itemId, 0);
            }
        }
        return total;
    }
}
