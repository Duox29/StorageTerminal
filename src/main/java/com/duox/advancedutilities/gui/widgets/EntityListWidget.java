package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for managing an EntityListSetting.
 * Supports adding/removing entity types.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// ARCHITECTURE FIX: Implemented dynamic height calculation and layout refresh handling
public class EntityListWidget extends SettingWidget {
    private final EntityListSetting setting;
    private EditBox idInput;
    private Runnable onRefreshCallback; // Store callback to trigger parent update
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 35;

    public EntityListWidget(EntityListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    /*
     * Calculates the height required to display all items based on the grid layout logic.
     * This ensures the parent GUI knows the correct size BEFORE rendering.
     */
    private int calculateContentHeight() {
        int count = setting.getValue().size();
        // Calculate items per row: (Width - Padding) / ItemSize
        // StartX is x+2, LimitX is x+width-18. Effective width roughly width-2.
        int itemsPerRow = (width - 4) / ITEM_SIZE;
        if (itemsPerRow < 1) itemsPerRow = 1;

        int rows = (int) Math.ceil((double) count / itemsPerRow);
        // Minimum 0 rows, but we need at least the input area
        int neededHeight = INPUT_AREA_HEIGHT + (rows * ITEM_SIZE) + 4; // +4 padding bottom
        return Math.max(height, neededHeight);
    }

    @Override
    public int getHeight() {
        // Always calculate fresh height based on data
        return calculateContentHeight();
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        this.onRefreshCallback = onRefresh; // Capture the callback
        Minecraft mc = Minecraft.getInstance();

        idInput = new EditBox(mc.font, x, y + 12, width - 45, 18, Component.literal("Entity ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
                    if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                        setting.add(BuiltInRegistries.ENTITY_TYPE.get(rl));
                        ConfigManager.getInstance().save();
                        idInput.setValue("");
                        if (onRefreshCallback != null) onRefreshCallback.run(); // Trigger layout update
                    }
                } catch (Exception ignored) {}
            }
        }).bounds(x + width - 40, y + 12, 40, 18).build();
        widgetConsumer.accept(btnAddId);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 2, 0xFFFFFF, false);

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        for (Map.Entry<EntityType<?>, Boolean> entry : setting.getValue().entrySet()) {
            // Logic must match calculateContentHeight
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            EntityType<?> type = entry.getKey();
            boolean enabled = entry.getValue();

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(currentX, currentY, currentX + 16, currentY + 16, bgColor);

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                guiGraphics.renderOutline(currentX, currentY, 16, 16, 0xFFFFFFFF);
                guiGraphics.renderTooltip(mc.font, Component.literal(type.getDescription().getString() + (enabled ? " [ON]" : " [OFF]")), mouseX, mouseY);
            }

            SpawnEggItem eggItem = DeferredSpawnEggItem.byId(type);
            if (eggItem != null) {
                guiGraphics.renderItem(eggItem.getDefaultInstance(), currentX, currentY);
            } else {
                guiGraphics.drawString(mc.font, "?", currentX + 5, currentY + 4, 0xAAAAAA);
            }

            currentX += ITEM_SIZE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        List<EntityType<?>> keys = new ArrayList<>(setting.getValue().keySet());
        for (EntityType<?> type : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                if (button == 0) {
                    setting.toggle(type);
                } else if (button == 1) {
                    setting.remove(type);
                    // Removing an item changes height, so we must refresh layout
                    if (onRefreshCallback != null) onRefreshCallback.run();
                }
                ConfigManager.getInstance().save();
                return true;
            }
            currentX += ITEM_SIZE;
        }
        return false;
    }
}