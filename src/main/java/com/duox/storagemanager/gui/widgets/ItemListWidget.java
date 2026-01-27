package com.duox.storagemanager.gui.widgets;
/*
 * Widget for managing an ItemListSetting.
 * Supports adding/removing items.
 */
import com.duox.storagemanager.system.ConfigManager;
import com.duox.storagemanager.system.settings.ItemListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent; // FIX: Added Import

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ItemListWidget extends SettingWidget {
    private final ItemListSetting setting;
    private EditBox idInput;
    private Runnable onRefreshCallback;
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 35;

    public ItemListWidget(ItemListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    private int calculateContentHeight() {
        int count = setting.getValue().size();
        int itemsPerRow = (width - 4) / ITEM_SIZE;
        if (itemsPerRow < 1) itemsPerRow = 1;
        int rows = (int) Math.ceil((double) count / itemsPerRow);
        int neededHeight = INPUT_AREA_HEIGHT + (rows * ITEM_SIZE) + 4;
        return Math.max(height, neededHeight);
    }

    @Override
    public int getHeight() {
        return calculateContentHeight();
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        this.onRefreshCallback = onRefresh;
        Minecraft mc = Minecraft.getInstance();

        idInput = new EditBox(mc.font, x, y + 12, width - 45, 18, Component.literal("Item ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                try {
                    Identifier rl = Identifier.tryParse(val.contains(":") ? val : "minecraft:" + val);

                    // FIX: Handle Optional return from Registry
                    if (rl != null) {
                        var optionalItem = BuiltInRegistries.ITEM.get(rl);
                        if (optionalItem.isPresent()) {
                            setting.add(optionalItem.get().value()); // Extract Item
                            ConfigManager.getInstance().save();
                            idInput.setValue("");
                            if (onRefreshCallback != null) onRefreshCallback.run();
                        }
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

        for (Map.Entry<Item, Boolean> entry : setting.getValue().entrySet()) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            Item item = entry.getKey();
            boolean enabled = entry.getValue();

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(currentX, currentY, currentX + 16, currentY + 16, bgColor);

            // Create a stack for rendering and name retrieval
            ItemStack stack = new ItemStack(item);

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                guiGraphics.renderOutline(currentX, currentY, 16, 16, 0xFFFFFFFF);

                // FIX: Use item.getName(stack) to get the localized name
                String name = item.getName(stack).getString();

                guiGraphics.setTooltipForNextFrame(
                        mc.font,
                        Component.literal(name + (enabled ? " [ON]" : " [OFF]")),
                        mouseX,
                        mouseY
                );
            }

            guiGraphics.renderItem(stack, currentX, currentY);

            currentX += ITEM_SIZE;
        }
    }
    // FIX: Updated signature to match parent class (SettingWidget) and new Input System
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        // Extract data
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        List<Item> keys = new ArrayList<>(setting.getValue().keySet());
        for (Item item : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                if (button == 0) {
                    setting.toggle(item);
                } else if (button == 1) {
                    setting.remove(item);
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