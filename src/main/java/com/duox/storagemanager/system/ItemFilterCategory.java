package com.duox.storagemanager.system;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public enum ItemFilterCategory {
    ALL("All"),
    BLOCKS("Blocks"),
    ORES("Resources"), // Đổi tên hiển thị thành Resources cho tổng quát
    TOOLS_ARMOR("Gear"),
    MISC("Misc");

    public final String displayName;

    ItemFilterCategory(String displayName) {
        this.displayName = displayName;
    }

    // Logic kiểm tra xem 1 ItemStack có thuộc danh mục này không
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        switch (this) {
            case ALL:
                return true;

            case BLOCKS:
                // Bắt chuẩn nhất mọi block có thể đặt xuống (bao gồm cả máy móc mod)
                return stack.getItem() instanceof BlockItem;

            case ORES:
                // 1. Quặng dạng Khối (Ore Blocks) của Vanilla
                if (stack.is(ItemTags.COAL_ORES) || stack.is(ItemTags.IRON_ORES) ||
                        stack.is(ItemTags.GOLD_ORES) || stack.is(ItemTags.LAPIS_ORES) ||
                        stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.REDSTONE_ORES) ||
                        stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.COPPER_ORES)) {
                    return true;
                }

                // 2. Tài nguyên thành phẩm Vanilla
                // TRIM_MATERIALS bao gồm: Iron, Copper, Gold, Lapis, Emerald, Diamond, Netherite, Quartz, Amethyst, Redstone
                // COALS bao gồm: Coal, Charcoal
                if (stack.is(ItemTags.TRIM_MATERIALS) || stack.is(ItemTags.COALS)) {
                    return true;
                }

                // 3. Quét linh hoạt bằng Registry Name (Bắt trọn Modded Items & Raw Materials)
                // Lấy tên ID của item (ví dụ: "raw_iron", "copper_ingot", "ruby_gem")
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

                return itemId.contains("raw_") ||
                        itemId.endsWith("_ingot") ||
                        itemId.endsWith("_nugget") ||
                        itemId.endsWith("_gem") ||
                        itemId.endsWith("_dust") ||
                        itemId.endsWith("_scrap") ||
                        itemId.contains("amethyst"); // Bắt thêm các mảnh amethyst shard

            case TOOLS_ARMOR:
                // Dùng hệ thống tag ENCHANTABLE để gom trọn mọi loại Gear (Tự động hỗ trợ Mod)
                return stack.is(ItemTags.ARMOR_ENCHANTABLE) ||
                        stack.is(ItemTags.WEAPON_ENCHANTABLE) ||
                        stack.is(ItemTags.MINING_ENCHANTABLE) ||
                        stack.is(ItemTags.FISHING_ENCHANTABLE) ||
                        stack.is(ItemTags.BOW_ENCHANTABLE) ||
                        stack.is(ItemTags.CROSSBOW_ENCHANTABLE) ||
                        stack.is(ItemTags.TRIDENT_ENCHANTABLE) ||
                        stack.is(ItemTags.MACE_ENCHANTABLE) ||
                        // Bổ sung các công cụ thuần túy
                        stack.is(ItemTags.SWORDS) ||
                        stack.is(ItemTags.AXES) ||
                        stack.is(ItemTags.PICKAXES) ||
                        stack.is(ItemTags.SHOVELS) ||
                        stack.is(ItemTags.HOES) ||
                        stack.is(ItemTags.TRIMMABLE_ARMOR);

            case MISC:
                // MISC là tất cả những gì không lọt vào 3 bộ lọc trên
                return !BLOCKS.matches(stack) && !ORES.matches(stack) && !TOOLS_ARMOR.matches(stack);

            default:
                return true;
        }
    }
}