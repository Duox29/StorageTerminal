package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.factory.WidgetFactory;
import com.duox.storagemanager.gui.widgets.KeybindWidget;
import com.duox.storagemanager.gui.widgets.SettingWidget;
import com.duox.storagemanager.system.*;
import com.duox.storagemanager.system.Constants;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.settings.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Main GUI screen for the Advanced Utilities mod.
 * Displays modules organized by category and allows configuration of module settings.
 */
public class UtilityGui extends Screen {

    private static final int TOP_BAR_HEIGHT = Constants.GUI_TOP_BAR_HEIGHT;
    private static final int SIDEBAR_WIDTH = Constants.GUI_SIDEBAR_WIDTH;
    private static final int MODULE_BTN_HEIGHT = Constants.GUI_MODULE_BTN_HEIGHT;
    private static final int MODULE_BTN_WIDTH = Constants.GUI_MODULE_BTN_WIDTH;
    private static final int PADDING = Constants.GUI_PADDING;

    private int currentTabIndex = 0;
    private final List<Category> categories = new ArrayList<>();
    private List<Module> activeModulesSnapshot;
    private Module selectedModule = null;

    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<SettingWidget> customRenderWidgets = new ArrayList<>();

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    // ... (init method remains unchanged) ...
    @Override
    protected void init() {
        super.init();
        categories.clear();
        Collections.addAll(categories, Category.values());

        activeModulesSnapshot = ModuleManager.INSTANCE.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        if (selectedModule != null) {
            initSettingsPanel(selectedModule);
        }
    }

    private void initSettingsPanel(Module module) {
        // Clear old widgets
        for (AbstractWidget w : dynamicWidgets) this.removeWidget(w);
        dynamicWidgets.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        if (module == null) return;

        int startX = SIDEBAR_WIDTH + Constants.GUI_SETTINGS_START_X_OFFSET;
        int startY = TOP_BAR_HEIGHT + Constants.GUI_SETTINGS_START_Y_OFFSET;
        int widgetWidth = Constants.GUI_SETTINGS_WIDGET_WIDTH;

        // Add Keybind Widget
        KeybindWidget keybindWidget = new KeybindWidget(startX, startY, widgetWidth, 20, module.getKeyMapping());
        this.addRenderableWidget(keybindWidget);
        this.dynamicWidgets.add(keybindWidget);
        startY += 20 + PADDING;
        for (Setting<?> setting : module.getSettings()) {
            SettingWidget widget = WidgetFactory.create(setting, startX, startY, widgetWidth, 20);

            if (widget != null) {
                widget.init(w -> {
                    this.addRenderableWidget(w);
                    this.dynamicWidgets.add(w);
                }, () -> this.initSettingsPanel(this.selectedModule));

                this.customRenderWidgets.add(widget);
                startY += widget.getHeight() + PADDING;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(GuiGraphicsExtractor, mouseX, mouseY, partialTick);

        // 1. Draw Background
        GuiGraphicsExtractor.fill(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, this.height, 0xAA000000);
        GuiGraphicsExtractor.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x80000000);
        GuiGraphicsExtractor.verticalLine(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.height, 0xFFFFFFFF);
        GuiGraphicsExtractor.horizontalLine(0, this.width, TOP_BAR_HEIGHT, 0xFFFFFFFF);
        GuiGraphicsExtractor.text(this.font, "Adv. Utils", 10, 11, 0xFFFFFFFF, false);

        // 2. Draw Tabs
        int tabX = 80;
        boolean isActiveTab = (currentTabIndex == 0);
        drawTabButton(GuiGraphicsExtractor, tabX, 2, 60, 26, "ACTIVE", isActiveTab, mouseX, mouseY);
        tabX += 65;
        for (int i = 0; i < categories.size(); i++) {
            boolean isSelected = (currentTabIndex == i + 1);
            drawTabButton(GuiGraphicsExtractor, tabX, 2, 60, 26, categories.get(i).name(), isSelected, mouseX, mouseY);
            tabX += 65;
        }

        // 3. Draw Module List
        renderModuleList(GuiGraphicsExtractor, mouseX, mouseY);

        // 4. Draw Settings
        if (selectedModule != null) {
            GuiGraphicsExtractor.text(this.font, "Settings: " + selectedModule.getName(), SIDEBAR_WIDTH + 20, TOP_BAR_HEIGHT + 15, 0xFFFFFF00, false);
            for (SettingWidget w : customRenderWidgets) {
                w.render(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
            }
        } else {
            GuiGraphicsExtractor.centeredText(this.font, "Select a module to edit settings",
                    SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2, this.height / 2, 0xFFAAAAAA);
        }

        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }

    private void renderModuleList(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
        List<Module> modulesToDisplay = (currentTabIndex == 0) ? activeModulesSnapshot : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
        int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
        int btnY = TOP_BAR_HEIGHT + 10;

        if (modulesToDisplay.isEmpty()) {
            GuiGraphicsExtractor.centeredText(this.font, "Empty", SIDEBAR_WIDTH / 2, btnY, 0xFFAAAAAA);
            return;
        }

        for (Module mod : modulesToDisplay) {
            boolean isHovered = isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT);
            boolean isSelected = (mod == selectedModule);
            int color = mod.isEnabled() ? 0xFF2ECC71 : 0xFFE74C3C;
            if (isHovered) color = darken(color);

            GuiGraphicsExtractor.fill(btnX, btnY, btnX + MODULE_BTN_WIDTH, btnY + MODULE_BTN_HEIGHT, color);
            if (isSelected) GuiGraphicsExtractor.outline(btnX - 1, btnY - 1, MODULE_BTN_WIDTH + 2, MODULE_BTN_HEIGHT + 2, 0xFF3498DB);
            GuiGraphicsExtractor.centeredText(this.font, mod.getName(), btnX + MODULE_BTN_WIDTH / 2, btnY + 7, 0xFFFFFFFF);
            if (isHovered) {
                GuiGraphicsExtractor.setTooltipForNextFrame(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
            }
            btnY += MODULE_BTN_HEIGHT + PADDING;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        // 1. Extract raw data for your internal logic
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (mouseX > SIDEBAR_WIDTH) {
            for (SettingWidget w : customRenderWidgets) {
                // NOTE: If SettingWidget is a custom class you made, this might work.
                // If it extends AbstractWidget, you might need to change this to: w.mouseClicked(event, isFocused);
                if (w.mouseClicked(event, isFocused)) return true;            }
            // FIX: Pass the event object to super
            if (super.mouseClicked(event, isFocused)) return true;
        }

        if (mouseY < TOP_BAR_HEIGHT) {
            int tabX = 80;
            if (isInside(mouseX, mouseY, tabX, 2, 60, 26)) { currentTabIndex = 0; return true; }
            tabX += 65;
            for (int i = 0; i < categories.size(); i++) {
                if (isInside(mouseX, mouseY, tabX, 2, 60, 26)) { currentTabIndex = i + 1; return true; }
                tabX += 65;
            }
        }
        else if (mouseX <= SIDEBAR_WIDTH) {
            List<Module> modulesToDisplay = (currentTabIndex == 0) ? activeModulesSnapshot : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
            int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
            int btnY = TOP_BAR_HEIGHT + 10;
            for (Module mod : modulesToDisplay) {
                if (isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT)) {
                    if (button == 0) { mod.toggle(); ConfigManager.getInstance().save(); }
                    else if (button == 1 || mod == selectedModule) { initSettingsPanel(mod); }
                    return true;
                }
                btnY += MODULE_BTN_HEIGHT + PADDING;
            }
        }
        return false;
    }
    private boolean isInside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    private int darken(int color) { Color c = new Color(color); return new Color((int)(c.getRed() * 0.7), (int)(c.getGreen() * 0.7), (int)(c.getBlue() * 0.7)).getRGB(); }
    private void drawTabButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String t, boolean s, int mx, int my) {
        int c = s ? 0xFF3498DB : 0xFF2C3E50;
        if (isInside(mx, my, x, y, w, h) && !s) c = 0xFF34495E;
        g.fill(x, y, x + w, y + h, c);
        g.centeredText(this.font, t, x + w / 2, y + 8, s ? 0xFFFFFF00 : 0xFFAAAAAA);
    }
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Để trống để tắt hoàn toàn background blur
    }
}
