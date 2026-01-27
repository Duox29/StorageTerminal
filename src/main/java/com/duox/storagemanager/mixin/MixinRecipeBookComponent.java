package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {

    @Shadow
    protected Minecraft minecraft;

    /**
     * Inject into tryPlaceRecipe to intercept recipe clicks.
     * This method was identified existing in MC 1.21.11 via debugging.
     */
    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"))
    private void onTryPlaceRecipe(RecipeCollection collection,
            RecipeDisplayId id,
            boolean placeAll,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (sm == null || !sm.isEnabled() || !sm.autoRequestRecipe.getValue()) {
                return;
            }

            // Get ClientRecipeBook to resolve ID to Display
            if (minecraft.player == null)
                return;
            var book = minecraft.player.getRecipeBook();

            // Use accessor to get the known map
            if (book instanceof ClientRecipeBookAccessor accessor) {
                var known = accessor.getKnown();
                var entry = known.get(id);
                if (entry != null) {
                    RecipeDisplay display = entry.display();
                    requestIngredientsFromDisplay(display, sm);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process recipe ingredients from RecipeDisplay and request missing items from
     * storage
     */
    @SuppressWarnings("unchecked")
    private void requestIngredientsFromDisplay(RecipeDisplay display, StorageManager sm) {
        Map<String, Integer> needed = new HashMap<>();

        try {
            List<SlotDisplay> ingredientSlots = null;

            // Robust reflection: find any field returning List that contains SlotDisplay
            // This avoids issues with obfuscated field names or record component names
            for (java.lang.reflect.Field field : display.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (List.class.isAssignableFrom(field.getType())) {
                    try {
                        List<?> list = (List<?>) field.get(display);
                        if (list != null && !list.isEmpty()
                                && list.get(0) instanceof SlotDisplay) {
                            ingredientSlots = (List<SlotDisplay>) list;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // If field access failed (e.g. record), try accessor methods
            if (ingredientSlots == null) {
                for (java.lang.reflect.Method method : display.getClass().getDeclaredMethods()) {
                    if (List.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 0) {
                        try {
                            List<?> list = (List<?>) method.invoke(display);
                            if (list != null && !list.isEmpty()
                                    && list.get(0) instanceof SlotDisplay) {
                                ingredientSlots = (List<SlotDisplay>) list;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (ingredientSlots == null)
                return;

            for (SlotDisplay slotDisplay : ingredientSlots) {
                // SlotDisplay needs to be converted to ItemStacks
                List<ItemStack> possibleItems = resolveSlotDisplay(slotDisplay);

                if (!possibleItems.isEmpty()) {
                    // Priority: Item already in cache
                    String bestItemId = null;
                    for (ItemStack stack : possibleItems) {
                        if (stack.isEmpty())
                            continue;
                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (hasInCache(id)) {
                            bestItemId = id;
                            break;
                        }
                    }

                    // If not in cache, use first variant
                    if (bestItemId == null && !possibleItems.get(0).isEmpty()) {
                        bestItemId = BuiltInRegistries.ITEM.getKey(possibleItems.get(0).getItem()).toString();
                    }

                    if (bestItemId != null) {
                        needed.put(bestItemId, needed.getOrDefault(bestItemId, 0) + 1);
                    }
                }
            }

            // Check what's already in inventory
            Inventory inventory = minecraft.player.getInventory();

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
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

            // Add to Request Queue and start retrieval
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resolve a SlotDisplay to a list of possible ItemStacks
     */
    private List<ItemStack> resolveSlotDisplay(SlotDisplay slotDisplay) {
        List<ItemStack> result = new java.util.ArrayList<>();

        // Use a simple visitor pattern to extract items from SlotDisplay
        if (slotDisplay instanceof SlotDisplay.ItemStackSlotDisplay itemDisplay) {
            result.add(itemDisplay.stack());
        } else if (slotDisplay instanceof SlotDisplay.ItemSlotDisplay itemSlotDisplay) {
            // Convert Item to ItemStack
            result.add(new ItemStack(itemSlotDisplay.item()));
        } else if (slotDisplay instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            // For tag displays, iterate through registry to find items with this tag
            var tagKey = tagDisplay.tag();

            // Iterate through all items and check if they have this tag
            for (var item : BuiltInRegistries.ITEM) {
                // Check if this item belongs to the tag
                var holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
                if (holder.is(tagKey)) {
                    result.add(new ItemStack(item));
                    // Only take first few items from tag to avoid too many results
                    if (result.size() >= 5)
                        break;
                }
            }
        }

        return result;
    }

    private boolean hasInCache(String itemId) {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        return sm != null && sm.hasItemInCache(itemId);
    }
}