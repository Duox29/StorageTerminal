package com.duox.storagemanager.gui;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ConfigManager;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.system.settings.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/* Module list and settings panel. */
public class UtilityGui extends Screen {
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int MODULE_BTN_WIDTH = 100;
    private static final int MODULE_BTN_HEIGHT = 22;
    private static final int PADDING = 5;
    private static final int DETAILS_LEFT = SIDEBAR_WIDTH + 20;
    private static final int LINE_HEIGHT = 12;
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 18;
    private static final int WIDGET_SPACING = 3;
    private static final int WIDGETS_TOP = TOP_BAR_HEIGHT + 62;

    private static final int MODULE_LIST_TOP = TOP_BAR_HEIGHT + 10;

    private Module selectedModule;
    private final List<AbstractWidget> settingWidgets = new ArrayList<>();

    public UtilityGui() {
        super(Component.literal("Storage Manager"));
    }

    @Override
    protected void init() {
        super.init();
        List<Module> modules = ModuleManager.INSTANCE.getModules();
        if (selectedModule == null && !modules.isEmpty()) {
            selectedModule = modules.getFirst();
        }
        rebuildSettings();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, this.height, 0xAA000000);
        graphics.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x80000000);
        graphics.verticalLine(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.height, 0xFFFFFFFF);
        graphics.horizontalLine(0, this.width, TOP_BAR_HEIGHT, 0xFFFFFFFF);
        graphics.text(this.font, "Module Manager", 10, 11, 0xFFFFFFFF, false);

        renderModuleList(graphics, mouseX, mouseY);
        renderModuleDetails(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderModuleList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Module> modules = ModuleManager.INSTANCE.getModules();
        int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;

        if (modules.isEmpty()) {
            graphics.centeredText(this.font, "No modules", SIDEBAR_WIDTH / 2, MODULE_LIST_TOP, 0xFFAAAAAA);
            return;
        }

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int btnY = moduleButtonY(i);

            boolean hovered = isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT);
            int color = module.isEnabled() ? 0xFF2ECC71 : 0xFFE74C3C;
            if (hovered) color = darken(color);

            graphics.fill(btnX, btnY, btnX + MODULE_BTN_WIDTH, btnY + MODULE_BTN_HEIGHT, color);
            if (module == selectedModule) {
                graphics.outline(btnX - 1, btnY - 1, MODULE_BTN_WIDTH + 2, MODULE_BTN_HEIGHT + 2, 0xFF3498DB);
            }
            graphics.centeredText(this.font, module.getName(), btnX + MODULE_BTN_WIDTH / 2, btnY + 7, 0xFFFFFFFF);
        }
    }

    private static int moduleButtonY(int index) {
        return MODULE_LIST_TOP + index * (MODULE_BTN_HEIGHT + PADDING);
    }

    private void renderModuleDetails(GuiGraphicsExtractor graphics) {
        if (selectedModule == null) {
            graphics.centeredText(this.font, "Select a module",
                    SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        int y = TOP_BAR_HEIGHT + 12;
        graphics.text(this.font, selectedModule.getName(), DETAILS_LEFT, y, 0xFFFFFF00, false);
        y += 16;
        graphics.text(this.font, "Status: " + (selectedModule.isEnabled() ? "Enabled" : "Disabled"),
                DETAILS_LEFT, y, selectedModule.isEnabled() ? 0xFF55FF55 : 0xFFFF5555, false);
        y += LINE_HEIGHT;
        graphics.text(this.font, selectedModule.getDescription(), DETAILS_LEFT, y, 0xFFAAAAAA, false);
    }

    private void rebuildSettings() {
        this.setFocused(null);
        for (AbstractWidget widget : settingWidgets) {
            this.removeWidget(widget);
        }
        settingWidgets.clear();

        if (selectedModule == null) return;

        int x = DETAILS_LEFT;
        int y = WIDGETS_TOP;
        addSettingWidget(new KeybindButton(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, selectedModule));
        y += WIDGET_HEIGHT + WIDGET_SPACING;

        for (Setting<?> setting : selectedModule.getSettings()) {
            if (setting instanceof BooleanSetting bool) {
                addSettingWidget(createBooleanButton(bool, x, y));
                y += WIDGET_HEIGHT + WIDGET_SPACING;
            } else if (setting instanceof NumberSetting number) {
                addSettingWidget(createNumberSlider(number, x, y));
                y += WIDGET_HEIGHT + WIDGET_SPACING;
            }
        }
    }

    private void addSettingWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
        settingWidgets.add(widget);
    }

    private static Button createBooleanButton(BooleanSetting setting, int x, int y) {
        Button button = Button.builder(booleanLabel(setting), b -> {
            setting.toggle();
            ConfigManager.getInstance().save();
            b.setMessage(booleanLabel(setting));
            b.setFGColor(setting.getValue() ? 0x55FF55 : 0xFFAAAAAA);
        }).bounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT).build();
        button.setFGColor(setting.getValue() ? 0x55FF55 : 0xFFAAAAAA);
        return button;
    }

    private static AbstractSliderButton createNumberSlider(NumberSetting setting, int x, int y) {
        double initial = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        return new AbstractSliderButton(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.literal(setting.getName() + ": " + formatNumber(setting.getValue())), initial) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(setting.getName() + ": " + formatNumber(currentValue())));
            }

            @Override
            protected void applyValue() {
                setting.setValue(currentValue());
                ConfigManager.getInstance().save();
            }

            private double currentValue() {
                double value = this.value * (setting.getMax() - setting.getMin()) + setting.getMin();
                double increment = setting.getIncrement();
                if (increment > 0) {
                    value = Math.round(value / increment) * increment;
                }
                return value;
            }
        };
    }

    private static Component booleanLabel(BooleanSetting setting) {
        return Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF"));
    }

    private static String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format("%.1f", value);
    }

    private static final class KeybindButton extends Button {
        private final Module module;
        private boolean listening;

        private KeybindButton(int x, int y, int width, int height, Module module) {
            super(x, y, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
            this.module = module;
            updateMessage();
        }

        @Override
        public void onPress(InputWithModifiers input) {
            this.listening = true;
            updateMessage();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            this.extractDefaultSprite(graphics);
            this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!listening) return super.keyPressed(event);

            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
            } else {
                module.getKeyMapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
                Minecraft.getInstance().options.save();
                KeyMapping.resetMapping();
                listening = false;
            }
            updateMessage();
            return true;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (!listening) return super.mouseClicked(event, isFocused);

            module.getKeyMapping().setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
            listening = false;
            updateMessage();
            return true;
        }

        private void updateMessage() {
            if (listening) {
                setMessage(Component.literal("> Press Key <").withStyle(ChatFormatting.YELLOW));
            } else {
                setMessage(Component.literal("Keybind: "
                        + module.getKeyMapping().getKey().getDisplayName().getString()));
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (super.mouseClicked(event, isFocused)) return true;

        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseY < TOP_BAR_HEIGHT || mouseX > SIDEBAR_WIDTH) return false;
        if (ModuleManager.INSTANCE.getModules().isEmpty()) return false;

        int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
        List<Module> modules = ModuleManager.INSTANCE.getModules();
        for (int i = 0; i < modules.size(); i++) {
            int btnY = moduleButtonY(i);
            if (isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT)) {
                selectedModule = modules.get(i);
                rebuildSettings();
                return true;
            }
        }
        return false;
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.7);
        int g = (int) (((color >> 8) & 0xFF) * 0.7);
        int b = (int) ((color & 0xFF) * 0.7);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No background blur.
    }
}
