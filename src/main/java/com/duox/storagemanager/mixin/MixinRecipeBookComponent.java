package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ToastUtils;
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

/**
 * MixinRecipeBookComponent
 * Intercepts recipe clicks in the Recipe Book to automatically request
 * missing ingredients from the StorageManager.
 */
@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {

    @Shadow
    protected Minecraft minecraft;

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

            if (minecraft.player == null) return;
            var book = minecraft.player.getRecipeBook();

            if (book instanceof ClientRecipeBookAccessor accessor) {
                var known = accessor.getKnown();
                var entry = known.get(id);
                if (entry != null) {
                    RecipeDisplay display = entry.display();
                    // TRUYỀN THÊM id VÀO ĐÂY
                    requestIngredientsFromDisplay(display, id, sm, placeAll);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void requestIngredientsFromDisplay(RecipeDisplay display, RecipeDisplayId id, StorageManager sm, boolean placeAll) {
        Map<String, Integer> baseCost = new HashMap<>();

        try {
            List<SlotDisplay> ingredientSlots = null;

            // Robust reflection to find ingredient list
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
                    } catch (Exception ignored) {}
                }
            }

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
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (ingredientSlots == null) return;

            // 1. TÍNH BASE COST: Phân tích các nguyên liệu cần thiết cho ĐÚNG 1 LẦN CRAFT
            for (SlotDisplay slotDisplay : ingredientSlots) {
                List<ItemStack> possibleItems = resolveSlotDisplay(slotDisplay);

                if (!possibleItems.isEmpty()) {
                    String bestItemId = null;
                    int maxAvailable = -1;

                    // Ưu tiên chọn item mà chúng ta đang có NHIỀU NHẤT trong kho
                    for (ItemStack stack : possibleItems) {
                        if (stack.isEmpty()) continue;
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        int available = getTotalInCache(itemId);
                        if (available > maxAvailable) {
                            maxAvailable = available;
                            bestItemId = itemId;
                        }
                    }

                    // Fallback
                    if (bestItemId == null || maxAvailable <= 0) {
                        bestItemId = BuiltInRegistries.ITEM.getKey(possibleItems.get(0).getItem()).toString();
                    }

                    baseCost.put(bestItemId, baseCost.getOrDefault(bestItemId, 0) + 1);
                }
            }

            if (baseCost.isEmpty()) return;

            // 2. Tính số lượng item CÓ SẴN trong túi đồ của người chơi
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

            // 3. Xác định SỐ LẦN CRAFT TỐI ĐA (M)
            int M;
            if (!placeAll) {
                M = 1; // Click bình thường -> Chỉ craft 1 lần
            } else {
                M = Integer.MAX_VALUE; // Shift click -> Tính số lần tối đa có thể craft
                for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                    String reqId = entry.getKey();
                    int costPerCraft = entry.getValue();
                    int inCache = getTotalInCache(reqId);
                    int inPlayer = playerInv.getOrDefault(reqId, 0);

                    // Số lần craft tối đa theo lượng đồ thực có
                    int maxByAvailability = (inCache + inPlayer) / costPerCraft;
                    // Giới hạn max 64 item tổng cộng cho mỗi slot nguyên liệu
                    int maxByStack = 64 / costPerCraft;

                    M = Math.min(M, Math.min(maxByAvailability, maxByStack));
                }
            }

            // 4. KIỂM TRA ĐIỀU KIỆN (ALL OR NOTHING)
            Map<String, Integer> missingItems = new HashMap<>();
            boolean canCraft = true;

            if (M == 0) {
                // M = 0 nghĩa là người chơi KHÔNG ĐỦ ĐỒ ĐỂ CRAFT NGAY CẢ 1 LẦN.
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
                // Kiểm tra lại lần cuối để chắc chắn không có sự cố (thường M>0 là đủ)
                for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                    String reqId = entry.getKey();
                    int costPerCraft = entry.getValue();
                    int totalNeeded = M * costPerCraft;
                    int available = getTotalInCache(reqId) + playerInv.getOrDefault(reqId, 0);

                    if (available < totalNeeded) {
                        canCraft = false;
                        missingItems.put(reqId, totalNeeded - available);
                    }
                }
            }

            // 5. THỰC THI RÚT ĐỒ
            if (canCraft) {
                boolean addedAny = false;
                for (Map.Entry<String, Integer> entry : baseCost.entrySet()) {
                    String reqId = entry.getKey();
                    int costPerCraft = entry.getValue();
                    int totalNeeded = M * costPerCraft;
                    int inPlayer = playerInv.getOrDefault(reqId, 0);

                    // Chỉ rút phần CÒN THIẾU
                    int toPull = totalNeeded - inPlayer;
                    if (toPull > 0) {
                        sm.addToRequestQueue(reqId, toPull);
                        addedAny = true;
                    }
                }

                // Nếu có đồ cần lấy, bắt đầu quy trình
                if (addedAny) {
                    // FIX: Báo cho StorageManager biết công thức cần đặt sau khi lấy xong
                    sm.setPendingRecipe(id, placeAll);
                    sm.startRetrieval();
                }
            } else {
                // Báo lỗi thiếu đồ bằng Toast
                StringBuilder warning = new StringBuilder();
                boolean first = true;
                for (Map.Entry<String, Integer> missing : missingItems.entrySet()) {
                    if (!first) warning.append(", ");
                    String cleanName = missing.getKey().replace("minecraft:", "");
                    warning.append(missing.getValue()).append("x ").append(cleanName);
                    first = false;
                }
                com.duox.storagemanager.utils.ToastUtils.sendToast("§cMissing Ingredients", warning.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hàm tiện ích tính TỔNG số lượng của 1 loại item có trong toàn bộ mạng lưới cache.
     */
    private int getTotalInCache(String itemId) {
        Map<String, Map<String, Integer>> cache = com.duox.storagemanager.modules.AutoStash.getChestCache();
        if (cache == null) return 0;

        int total = 0;
        for (Map<String, Integer> chestContents : cache.values()) {
            if (chestContents != null && chestContents.containsKey(itemId)) {
                total += chestContents.get(itemId);
            }
        }
        return total;
    }

    /**
     * Resolve a SlotDisplay to a list of possible ItemStacks.
     */
    private List<ItemStack> resolveSlotDisplay(SlotDisplay slotDisplay) {
        return resolveSlotDisplaySafe(slotDisplay, new java.util.HashSet<>(), 0);
    }

    /**
     * Hàm đệ quy thực sự với cơ chế chống Crash (Cycle Detection & Depth Limit)
     */
    private List<ItemStack> resolveSlotDisplaySafe(SlotDisplay slotDisplay, java.util.Set<Object> visited, int depth) {
        List<ItemStack> result = new java.util.ArrayList<>();

        if (slotDisplay == null || depth > 5 || !visited.add(slotDisplay)) {
            return result;
        }

        if (slotDisplay instanceof SlotDisplay.ItemStackSlotDisplay itemDisplay) {
            result.add(itemDisplay.stack().create());
        } else if (slotDisplay instanceof SlotDisplay.ItemSlotDisplay itemSlotDisplay) {
            result.add(new ItemStack(itemSlotDisplay.item()));
        } else if (slotDisplay instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            var tagKey = tagDisplay.tag();
            for (var item : BuiltInRegistries.ITEM) {
                var holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
                if (holder.is(tagKey)) {
                    result.add(new ItemStack(item));
                    if (result.size() >= 5) break;
                }
            }
        } else {
            try {
                for (java.lang.reflect.Field field : slotDisplay.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object val = field.get(slotDisplay);

                    if (val instanceof SlotDisplay nestedDisplay) {
                        result.addAll(resolveSlotDisplaySafe(nestedDisplay, visited, depth + 1));
                    } else if (val instanceof java.util.List<?> list) {
                        for (Object obj : list) {
                            if (obj instanceof SlotDisplay nestedListDisplay) {
                                result.addAll(resolveSlotDisplaySafe(nestedListDisplay, visited, depth + 1));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return result;
    }
}