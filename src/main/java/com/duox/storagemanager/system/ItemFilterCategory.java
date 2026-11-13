package com.duox.storagemanager.system;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public enum ItemFilterCategory {
    ALL("All"),
    BLOCKS("Blocks"),
    ORES("Resources"),
    TOOLS_ARMOR("Gear"),
    MISC("Misc");

    public final String displayName;

    ItemFilterCategory(String displayName) {
        this.displayName = displayName;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        switch (this) {
            case ALL:
                return true;

            case BLOCKS:
                return stack.getItem() instanceof BlockItem;

            case ORES:
                if (stack.is(ItemTags.COAL_ORES) || stack.is(ItemTags.IRON_ORES) ||
                        stack.is(ItemTags.GOLD_ORES) || stack.is(ItemTags.LAPIS_ORES) ||
                        stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.REDSTONE_ORES) ||
                        stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.COPPER_ORES)) {
                    return true;
                }

                if (stack.is(ItemTags.TRIM_MATERIALS) || stack.is(ItemTags.COALS)) {
                    return true;
                }

                // Catch modded raw materials and crafting resources by registry name.
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

                return itemId.contains("raw_") ||
                        itemId.endsWith("_ingot") ||
                        itemId.endsWith("_nugget") ||
                        itemId.endsWith("_gem") ||
                        itemId.endsWith("_dust") ||
                        itemId.endsWith("_scrap") ||
                        itemId.contains("amethyst");

            case TOOLS_ARMOR:
                // Enchantable tags cover most vanilla and modded gear.
                return stack.is(ItemTags.ARMOR_ENCHANTABLE) ||
                        stack.is(ItemTags.WEAPON_ENCHANTABLE) ||
                        stack.is(ItemTags.MINING_ENCHANTABLE) ||
                        stack.is(ItemTags.FISHING_ENCHANTABLE) ||
                        stack.is(ItemTags.BOW_ENCHANTABLE) ||
                        stack.is(ItemTags.CROSSBOW_ENCHANTABLE) ||
                        stack.is(ItemTags.TRIDENT_ENCHANTABLE) ||
                        stack.is(ItemTags.MACE_ENCHANTABLE) ||
                        stack.is(ItemTags.SWORDS) ||
                        stack.is(ItemTags.AXES) ||
                        stack.is(ItemTags.PICKAXES) ||
                        stack.is(ItemTags.SHOVELS) ||
                        stack.is(ItemTags.HOES) ||
                        stack.is(ItemTags.TRIMMABLE_ARMOR);

            case MISC:
                return !BLOCKS.matches(stack) && !ORES.matches(stack) && !TOOLS_ARMOR.matches(stack);

            default:
                return true;
        }
    }
}