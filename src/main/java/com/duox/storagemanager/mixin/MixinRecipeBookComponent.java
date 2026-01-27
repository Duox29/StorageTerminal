package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.display.RecipeDisplayId; // FIX: Added Import
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {

    @Shadow
    protected Minecraft minecraft;

    // FIX 1: Updated signature to use RecipeDisplayId matching Minecraft 1.21.4
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V"))
    private void redirectHandlePlaceRecipe(MultiPlayerGameMode instance, int containerId, RecipeDisplayId recipeId, boolean placeAll) {
        // 1. Run original logic
        instance.handlePlaceRecipe(containerId, recipeId, placeAll);

        // 2. StorageManager Logic
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (sm != null && sm.isEnabled() && sm.autoRequestRecipe.getValue()) {
                // NOTE: RecipeDisplayId cannot be easily converted to RecipeHolder here without a lookup helper.
                // For now, this call is disabled to prevent runtime crashes until a lookup is implemented.
                // requestIngredients(recipeId, sm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Updated to accept RecipeHolder if you implement the lookup,
    // or you can try to adapt it to RecipeDisplayId if possible.
    private void requestIngredients(RecipeHolder<?> recipeHolder, StorageManager sm) {
        Map<String, Integer> needed = new HashMap<>();

        Recipe<?> recipe = recipeHolder.value();
        PlacementInfo info = recipe.placementInfo();

        // FIX 2: PlacementInfo.ingredients() returns List<Ingredient>, not List<Optional<Ingredient>>
        List<Ingredient> ingredients = info.ingredients();

        for (Ingredient ingredient : ingredients) {
            // FIX 3: ingredient.getItems() is removed. Use .items() stream.
            ItemStack[] items = ingredient.items()
                    .map(holder -> new ItemStack(holder.value()))
                    .toArray(ItemStack[]::new);

            if (items.length > 0) {
                // Priority: Item already in cache
                String bestItemId = null;
                for (ItemStack stack : items) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (hasInCache(id)) {
                        bestItemId = id;
                        break;
                    }
                }

                // If not in cache, use first variant
                if (bestItemId == null) {
                    bestItemId = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
                }

                needed.put(bestItemId, needed.getOrDefault(bestItemId, 0) + 1);
            }
        }

        // FIX 4: Removed the broken 'inventory.items' loop.
        // We use the standard accessor loop below which is safe.
        Inventory inventory = minecraft.player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (needed.containsKey(id)) {
                int count = needed.get(id);
                int inInv = stack.getCount();
                if (inInv >= count) {
                    needed.remove(id);
                } else {
                    needed.put(id, count - inInv);
                }
            }
        }

        // 3. Add to Request Queue
        if (!needed.isEmpty()) {
            boolean added = false;
            for (Map.Entry<String, Integer> entry : needed.entrySet()) {
                if (hasInCache(entry.getKey())) {
                    sm.addToRequestQueue(entry.getKey(), entry.getValue());
                    added = true;
                }
            }
            if (added) {
                sm.startRetrieval();
            }
        }
    }

    private boolean hasInCache(String itemId) {
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();
        for (Map<String, Integer> contents : cache.values()) {
            if (contents.containsKey(itemId) && contents.get(itemId) > 0)
                return true;
        }
        return false;
    }
}