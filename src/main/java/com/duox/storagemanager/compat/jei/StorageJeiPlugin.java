package com.duox.storagemanager.compat.jei;

import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ManualContainerTracker;
import com.duox.storagemanager.utils.RecipeIngredientRequests;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JeiPlugin
public final class StorageJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.parse("storagemanager:jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        var info = helper.createBasicRecipeTransferInfo(CraftingMenu.class, MenuType.CRAFTING,
                RecipeTypes.CRAFTING, 1, 9, 10, 36);
        registration.addRecipeTransferHandler(new Handler(helper, helper.createUnregisteredRecipeTransferHandler(info)),
                RecipeTypes.CRAFTING);
    }

    private record Handler(IRecipeTransferHandlerHelper helper,
                           IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> delegate)
            implements IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> {
        @Override public Class<CraftingMenu> getContainerClass() { return CraftingMenu.class; }
        @Override public Optional<MenuType<CraftingMenu>> getMenuType() { return Optional.of(MenuType.CRAFTING); }
        @Override public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() { return RecipeTypes.CRAFTING; }

        @Override
        public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, CraftingMenu> context,
                                                    boolean doTransfer) {
            Minecraft mc = Minecraft.getInstance();
            StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
            if (storage == null || !storage.isEnabled() || !storage.autoRequestRecipe.getValue()
                    || mc.player != context.getPlayer() || mc.level == null || !helper.recipeTransferHasServerSupport()) {
                return delegate.transferRecipe(context, doTransfer);
            }
            BlockPos table = ManualContainerTracker.getConfirmedManualContainer(context.getContainer().containerId);
            if (table == null) return delegate.transferRecipe(context, doTransfer);
            List<List<ItemStack>> ingredients = new ArrayList<>();
            for (IRecipeSlotView slot : context.getRecipeSlots().getSlotViews(RecipeIngredientRole.INPUT)) {
                if (slot.isEmpty()) continue;
                List<ItemStack> alternatives = slot.getItemStacks().filter(stack -> !stack.isEmpty()).toList();
                if (alternatives.isEmpty() || alternatives.stream().anyMatch(stack -> stack.getCount() != 1))
                    return delegate.transferRecipe(context, doTransfer);
                ingredients.add(alternatives);
            }
            Map<String, Integer> requests = new RecipeIngredientRequests(mc).plan(ingredients, context.isMaxTransfer());
            if (requests == null || requests.isEmpty()) return delegate.transferRecipe(context, doTransfer);
            if (!doTransfer) return null;
            var world = mc.level;
            var player = mc.player;
            boolean queued = StorageTransferCoordinator.retrieveRecipe(requests, table,
                    context.getContainer().containerId, screen -> {
                        if (screen == null || mc.level != world || mc.player != player || !storage.isEnabled()) {
                            context.completeRecipeTransfer(RecipeTransferResult.REJECTED);
                            return;
                        }
                        try {
                            @SuppressWarnings("removal")
                            IRecipeTransferError error = delegate.transferRecipe(screen.getMenu(), context.getRecipe(),
                                    context.getRecipeSlots(), player, context.isMaxTransfer(), true);
                            context.completeRecipeTransfer(error == null ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
                        } catch (RuntimeException exception) {
                            context.completeRecipeTransfer(RecipeTransferResult.REJECTED);
                            throw exception;
                        }
                    });
            if (queued) return null;
            context.completeRecipeTransfer(RecipeTransferResult.REJECTED);
            return helper.createUserErrorWithTooltip(Component.literal("Storage transfer is already in progress"));
        }

        @Override
        @SuppressWarnings("removal")
        public IRecipeTransferError transferRecipe(CraftingMenu container, RecipeHolder<CraftingRecipe> recipe,
                                                     IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
            return delegate.transferRecipe(container, recipe, slots, player, maxTransfer, doTransfer);
        }
    }
}
