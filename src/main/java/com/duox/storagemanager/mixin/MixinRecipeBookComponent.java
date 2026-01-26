package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.modules.AutoStash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {

    @Shadow
    protected Minecraft minecraft;

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/RecipeHolder;Z)V"))
    private void redirectHandlePlaceRecipe(MultiPlayerGameMode instance, int containerId, RecipeHolder<?> recipeHolder, boolean placeAll) {
        // 1. Run the original logic (send packet to server)
        instance.handlePlaceRecipe(containerId, recipeHolder, placeAll);

        // 2. StorageManager Logic
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (sm != null && sm.isEnabled() && sm.autoRequestRecipe.getValue()) {
                requestIngredients(recipeHolder, sm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestIngredients(RecipeHolder<?> recipeHolder, StorageManager sm) {
        Map<String, Integer> needed = new HashMap<>();

        // 1. Calculate ingredients needed for 1 craft
        for (Ingredient ingredient : recipeHolder.value().getIngredients()) {
            if (ingredient.isEmpty())
                continue;

            ItemStack[] items = ingredient.getItems();
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

        // 2. Check Player Inventory (deduct what we already have)
        if (minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (stack.isEmpty())
                    continue;
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
        }

        // 3. Add to Request Queue
        if (!needed.isEmpty()) {
            boolean added = false;
            for (Map.Entry<String, Integer> entry : needed.entrySet()) {
                // Only request if we know we have it in storage (cache check)
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
