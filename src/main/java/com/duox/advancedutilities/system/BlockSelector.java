package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.BlockListSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

/*
 * Handles block selection for BlockListSetting.
 * Allows users to right-click blocks in the world to add them to a block list.
 */
public class BlockSelector {
    public static final BlockSelector INSTANCE = new BlockSelector();

    private boolean isActive = false;
    private BlockListSetting currentSetting = null;

    /**
     * Initializes the block selector and registers event handlers.
     */
    public void init() {
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Starts block selection mode for the given setting.
     * Closes the GUI and displays instructions to the player.
     *
     * @param setting The BlockListSetting to add blocks to
     */
    public void startSelecting(BlockListSetting setting) {
        this.currentSetting = setting;
        this.isActive = true;

        // Close GUI so player can see the game
        Minecraft.getInstance().setScreen(null);

        // Display instructions
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Right-click a block to add it to the list!").withStyle(ChatFormatting.YELLOW), true
            );
        }
    }

    /**
     * Handles mouse input events for block selection.
     */
    @SubscribeEvent
    public void onMouseInput(InputEvent.InteractionKeyMappingTriggered event) {
        // Only process when in selection mode and right-click (Use Item key)
        if (!isActive || !event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {

            // Get block at crosshair position
            net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());

            // Add to setting
            if (currentSetting != null) {
                currentSetting.add(state.getBlock());

                // Display success message
                mc.player.displayClientMessage(
                        Component.literal("Added: " + state.getBlock().getName().getString()).withStyle(ChatFormatting.GREEN), true
                );
            }

            // Reset state
            isActive = false;
            currentSetting = null;

            // Cancel game event (prevent block placement or chest opening)
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}