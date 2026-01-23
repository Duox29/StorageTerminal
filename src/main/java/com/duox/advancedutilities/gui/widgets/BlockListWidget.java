package com.duox.advancedutilities.gui.widgets;
import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// ARCHITECTURE FIX: Implemented dynamic height calculation
public class BlockListWidget extends SettingWidget {
    private final BlockListSetting setting;
    private EditBox idInput;
    private Runnable onRefreshCallback;
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 35;

    public BlockListWidget(BlockListSetting setting, int x, int y, int width, int height) {
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

        idInput = new EditBox(mc.font, x, y + 12, width - 45, 18, Component.literal("Block ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
                if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                    setting.add(BuiltInRegistries.BLOCK.get(rl));
                    ConfigManager.getInstance().save();
                    idInput.setValue("");
                    if (onRefreshCallback != null) onRefreshCallback.run();
                }
            }
        }).bounds(x + width - 40, y + 12, 40, 18).build();
        widgetConsumer.accept(btnAddId);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 2, 0xFFFFFF, false);

        int btnAddX = x + width - 20;
        boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 10;
        guiGraphics.drawString(mc.font, "[+]", btnAddX, y + 2, isHoverAdd ? 0xFF2ECC71 : 0xFFAAAAAA, false);

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        for (Map.Entry<Block, Boolean> entry : setting.getValue().entrySet()) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            Block block = entry.getKey();
            boolean enabled = entry.getValue();

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(currentX, currentY, currentX + 16, currentY + 16, bgColor);

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                guiGraphics.renderOutline(currentX, currentY, 16, 16, 0xFFFFFFFF);
                guiGraphics.renderTooltip(mc.font, Component.literal(block.getName().getString() + (enabled ? " [ON]" : " [OFF]")), mouseX, mouseY);
            }

            guiGraphics.renderItem(new ItemStack(block), currentX, currentY);
            currentX += ITEM_SIZE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int btnAddX = x + width - 20;
        if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 10) {
            BlockSelector.INSTANCE.startSelecting(setting);
            return true;
        }

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        List<Block> keys = new ArrayList<>(setting.getValue().keySet());
        for (Block block : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                if (button == 0) setting.toggle(block);
                else if (button == 1) {
                    setting.remove(block);
                    if (onRefreshCallback != null) onRefreshCallback.run(); // Refresh layout on remove
                }
                ConfigManager.getInstance().save();
                return true;
            }
            currentX += ITEM_SIZE;
        }
        return false;
    }
}