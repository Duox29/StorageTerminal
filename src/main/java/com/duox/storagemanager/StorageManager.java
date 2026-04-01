package com.duox.storagemanager;

import com.duox.storagemanager.gui.UtilityGui;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;   // hoặc net.minecraft.util.Identifier tùy version
import org.lwjgl.glfw.GLFW;

public class StorageManager implements ClientModInitializer {

    // Category đăng ký theo kiểu Fabric 1.26.1 (đúng như bạn đã viết)
    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("storagemanager", "keys"));

    // KeyMapping kiểu static final
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        // Client setup
        ModuleManager.INSTANCE.init();        // chỉ load config
        // ConfigManager.getInstance().load(); // ← xóa dòng này để tránh load 2 lần

        // Đăng ký tất cả key mappings
        registerKeyBindings();

        // === REGISTER TICK EVENT CHO MODULE MANAGER (thay cho NeoForge.EVENT_BUS) ===
        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager.INSTANCE::onClientTick);

        // Xử lý phím mở GUI (giữ nguyên)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new UtilityGui());
            }
        });
    }

    private void registerKeyBindings() {
        // Đăng ký key chính
        KeyMappingHelper.registerKeyMapping(OPEN_GUI_KEY);

        // Đăng ký key của các module
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            if (module.getKeyMapping() != null) {
                KeyMappingHelper.registerKeyMapping(module.getKeyMapping());
            }
        }
    }
}