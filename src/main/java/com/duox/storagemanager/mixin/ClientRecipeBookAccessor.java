package com.duox.storagemanager.mixin;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * ClientRecipeBookAccessor
 * 
 * Mixin accessor to expose the private 'known' map in ClientRecipeBook.
 * Contains mapping from RecipeDisplayId to RecipeDisplayEntry.
 */
@Mixin(ClientRecipeBook.class)
public interface ClientRecipeBookAccessor {
    // "known" matches the private field name in ClientRecipeBook.java
    @Accessor("known")
    Map<RecipeDisplayId, RecipeDisplayEntry> getKnown();
}