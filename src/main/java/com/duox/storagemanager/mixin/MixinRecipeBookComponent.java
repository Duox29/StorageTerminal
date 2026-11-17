package com.duox.storagemanager.mixin;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.RecipeIngredientRequests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/* Requests missing recipe ingredients from storage. */
@Mixin(RecipeBookComponent.class)
public class MixinRecipeBookComponent {
    private static final Logger LOGGER = LogManager.getLogger();

    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"))
    private void onTryPlaceRecipe(RecipeCollection collection, RecipeDisplayId id, boolean placeAll,
                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (sm == null || !sm.isEnabled() || !sm.autoRequestRecipe.getValue()) {
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;

            RecipeDisplayEntry entry = collection.getRecipes().stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst()
                    .orElse(null);
            if (entry != null) {
                new RecipeIngredientRequests(minecraft)
                        .requestIngredientsFromEntry(entry, id, sm, placeAll);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to request recipe ingredients", e);
        }
    }

}
