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
                    requestIngredientsFromDisplay(display, sm);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void requestIngredientsFromDisplay(RecipeDisplay display, StorageManager sm) {
        Map<String, Integer> needed = new HashMap<>();

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

            // 1. Phân tích các nguyên liệu cần thiết (Lấy loại có nhiều nhất trong kho nếu dùng Tag)
            for (SlotDisplay slotDisplay : ingredientSlots) {
                List<ItemStack> possibleItems = resolveSlotDisplay(slotDisplay);

                if (!possibleItems.isEmpty()) {
                    String bestItemId = null;
                    int maxAvailable = -1;

                    // Quét các item hợp lệ, ưu tiên chọn item mà chúng ta đang có NHIỀU NHẤT trong kho
                    for (ItemStack stack : possibleItems) {
                        if (stack.isEmpty()) continue;
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        int available = getTotalInCache(itemId);
                        if (available > maxAvailable) {
                            maxAvailable = available;
                            bestItemId = itemId;
                        }
                    }

                    // Nếu không có món nào trong kho, fallback về item đầu tiên của recipe
                    if (bestItemId == null || maxAvailable <= 0) {
                        bestItemId = BuiltInRegistries.ITEM.getKey(possibleItems.get(0).getItem()).toString();
                    }

                    if (bestItemId != null) {
                        needed.put(bestItemId, needed.getOrDefault(bestItemId, 0) + 1);
                    }
                }
            }

            // 2. Trừ đi số lượng đã có sẵn trong túi đồ của người chơi
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

            // 3. KIỂM TRA ĐỦ ĐIỀU KIỆN (ALL OR NOTHING)
            if (!needed.isEmpty()) {
                boolean hasAll = true;
                Map<String, Integer> missingItems = new HashMap<>(); // Lưu danh sách đồ bị thiếu để báo lỗi

                // Duyệt qua danh sách cần thiết, so sánh với tổng kho
                for (Map.Entry<String, Integer> entry : needed.entrySet()) {
                    String reqId = entry.getKey();
                    int reqCount = entry.getValue();
                    int available = getTotalInCache(reqId);

                    if (available < reqCount) {
                        hasAll = false;
                        missingItems.put(reqId, reqCount - available);
                    }
                }

                // Nếu có đủ TẤT CẢ nguyên liệu mới bắt đầu chạy đi lấy
                if (hasAll) {
                    for (Map.Entry<String, Integer> entry : needed.entrySet()) {
                        sm.addToRequestQueue(entry.getKey(), entry.getValue());
                    }
                    sm.startRetrieval();
                } else {
                    // Nếu thiếu đồ, in ra màn hình chat những món còn thiếu và hủy lệnh
                    StringBuilder warning = new StringBuilder("§cMissing ingredients: ");
                    boolean first = true;
                    for (Map.Entry<String, Integer> missing : missingItems.entrySet()) {
                        if (!first) warning.append(", ");
                        String cleanName = missing.getKey().replace("minecraft:", "");
                        warning.append(missing.getValue()).append("x ").append(cleanName);
                        first = false;
                    }
                    ToastUtils.sendToast("§cMissing Ingredients", warning.toString());
                }
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
            result.add(itemDisplay.stack());
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