package com.duox.storagemanager.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemSerializer {

    public static String serialize(ItemStack stack) {
        if (stack.isEmpty()) return "";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        if (!stack.getComponentsPatch().isEmpty()) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return id;

                HolderLookup.Provider provider = mc.level.registryAccess();
                DataResult<Tag> result = ItemStack.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack);

                if (result.isSuccess()) {
                    Tag tag = result.getOrThrow();
                    if (tag instanceof CompoundTag compound) {
                        compound.remove("count"); // Xóa count để gộp item vào chung 1 ô
                        return compound.toString();
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return id;
    }

    public static ItemStack deserialize(String key) {
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;

        if (key.startsWith("{")) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return ItemStack.EMPTY;

                HolderLookup.Provider provider = mc.level.registryAccess();

                // Dùng chính xác tên hàm lấy từ bảng mapping của bạn
                CompoundTag tag = TagParser.parseCompoundFully(key);
                tag.putInt("count", 1);

                DataResult<ItemStack> result = ItemStack.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag);
                return result.isSuccess() ? result.getOrThrow() : ItemStack.EMPTY;

            } catch (CommandSyntaxException e) {
                return ItemStack.EMPTY;
            }
        } else {
            Identifier location = Identifier.parse(key);
            Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(Items.AIR);
            return new ItemStack(item);
        }
    }

    public static String getBaseId(String key) {
        if (key.startsWith("{")) {
            int idStart = key.indexOf("id:\"");
            if (idStart != -1) {
                int start = idStart + 4;
                int end = key.indexOf("\"", start);
                if (end != -1) {
                    return key.substring(start, end);
                }
            }
        }
        return key;
    }
}