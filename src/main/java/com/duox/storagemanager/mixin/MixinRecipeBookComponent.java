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

    @Redirect(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/RecipeHolder;Z)V"
            )
    )
    private void redirectHandlePlaceRecipe(
            MultiPlayerGameMode instance,
            int containerId,
            RecipeHolder<?> recipeHolder,
            boolean placeAll
    ) {
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);

            if (sm == null || !sm.isEnabled() || !sm.autoRequestRecipe.getValue()) {
                instance.handlePlaceRecipe(containerId, recipeHolder, placeAll);
                return;
            }

            if (minecraft.player == null) {
                instance.handlePlaceRecipe(containerId, recipeHolder, placeAll);
                return;
            }

            boolean startedRetrieval = requestIngredientsFromRecipe(recipeHolder, sm, placeAll);
            if (!startedRetrieval) {
                instance.handlePlaceRecipe(containerId, recipeHolder, placeAll);
            }

        } catch (Exception e) {
            e.printStackTrace();
            instance.handlePlaceRecipe(containerId, recipeHolder, placeAll);
        }
    }

    private boolean requestIngredientsFromRecipe(
            RecipeHolder<?> recipeHolder,
            StorageManager sm,
            boolean placeAll
    ) {
        Map<String, Integer> baseCost = new HashMap<>();
        for (Ingredient ingredient : recipeHolder.value().getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            ItemStack[] possibleItems = ingredient.getItems();
            if (possibleItems.length == 0) {
                continue;
            }

            String bestItemId = null;
            int maxAvailable = -1;

            for (ItemStack stack : possibleItems) {
                if (stack.isEmpty()) {
                    continue;
                }

                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int available = getTotalInCache(itemId);

                if (available > maxAvailable) {
                    maxAvailable = available;
                    bestItemId = itemId;
                }
            }

            if (bestItemId == null || maxAvailable <= 0) {
                bestItemId = BuiltInRegistries.ITEM.getKey(possibleItems[0].getItem()).toString();
            }

            baseCost.put(bestItemId, baseCost.getOrDefault(bestItemId, 0) + 1);
        }

        if (baseCost.isEmpty()) {
            return false;
        }

        Map<String, Integer> playerInv = new HashMap<>();
        Inventory inventory = minecraft.player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (baseCost.containsKey(itemId)) {
                playerInv.put(itemId, playerInv.getOrDefault(itemId, 0) + stack.getCount());
            }
        }

        int craftCount;

        if (!placeAll) {
            craftCount = 1;
        } else {
            craftCount = Integer.MAX_VALUE;

            for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                String reqId = entry.getKey();
                int costPerCraft = entry.getValue();

                int inCache = getTotalInCache(reqId);
                int inPlayer = playerInv.getOrDefault(reqId, 0);

                int maxByAvailability = (inCache + inPlayer) / costPerCraft;
                int maxByStack = 64 / costPerCraft;

                craftCount = Math.min(craftCount, Math.min(maxByAvailability, maxByStack));
            }
        }

        Map<String, Integer> missingItems = new HashMap<>();
        boolean canCraft = true;

        if (craftCount == 0) {
            canCraft = false;

            for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                String reqId = entry.getKey();
                int costPerCraft = entry.getValue();

                int available = getTotalInCache(reqId) + playerInv.getOrDefault(reqId, 0);

                if (available < costPerCraft) {
                    missingItems.put(reqId, costPerCraft - available);
                }
            }
        } else {
            for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                String reqId = entry.getKey();
                int costPerCraft = entry.getValue();

                int totalNeeded = craftCount * costPerCraft;
                int available = getTotalInCache(reqId) + playerInv.getOrDefault(reqId, 0);

                if (available < totalNeeded) {
                    canCraft = false;
                    missingItems.put(reqId, totalNeeded - available);
                }
            }
        }

        if (!canCraft) {
            showMissingToast(missingItems);
            return false;
        }
        boolean addedAny = false;

        for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
            String reqId = entry.getKey();
            int costPerCraft = entry.getValue();

            int totalNeeded = craftCount * costPerCraft;
            int inPlayer = playerInv.getOrDefault(reqId, 0);

            int toPull = totalNeeded - inPlayer;

            if (toPull > 0) {
                sm.addToRequestQueue(reqId, toPull);
                addedAny = true;
            }
        }

        if (addedAny) {
            sm.setPendingRecipe(recipeHolder.id(), placeAll);
            sm.startRetrieval();

            return true;
        }
        return false;
    }

    private void showMissingToast(Map<String, Integer> missingItems) {
        if (missingItems.isEmpty()) {
            return;
        }

        StringBuilder warning = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Integer> missing : missingItems.entrySet()) {
            if (!first) {
                warning.append(", ");
            }

            String cleanName = missing.getKey().replace("minecraft:", "");
            warning.append(missing.getValue()).append("x ").append(cleanName);

            first = false;
        }

        com.duox.storagemanager.utils.ToastUtils.sendToast(
                "§cMissing Ingredients",
                warning.toString()
        );
    }

    private int getTotalInCache(String itemId) {
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();

        if (cache == null) {
            return 0;
        }

        int total = 0;

        for (Map<String, Integer> chestContents : cache.values()) {
            if (chestContents != null && chestContents.containsKey(itemId)) {
                total += chestContents.get(itemId);
            }
        }

        return total;
    }
}