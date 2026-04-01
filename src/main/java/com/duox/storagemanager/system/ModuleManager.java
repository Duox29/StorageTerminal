package com.duox.storagemanager.system;

import com.duox.storagemanager.gui.StorageScreen;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.utils.CacheUtils;
import com.duox.storagemanager.utils.ChestCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Manages all modules in the mod.
 * Handles registration, lookup, and tick processing for modules.
 */
public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    // Use a Map for O(1) lookup by class instead of looping
    private final Map<Class<? extends Module>, Module> moduleMap = new LinkedHashMap<>();

    private ModuleManager() {
        // Player category modules
        register(new AutoStash());
        register(new StorageManager());
    }

    public void register(Module module) {
        moduleMap.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleMap.get(clazz);
    }

    public List<Module> getModules() {
        return new ArrayList<>(moduleMap.values());
    }

    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigManager.getInstance().save();
    }

    public List<Module> getModulesByCategory(Category category) {
        return moduleMap.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Handles client tick events (Fabric style).
     * Parameter là MinecraftClient (đúng với ClientTickEvents.END_CLIENT_TICK).
     */
    public void onClientTick(Minecraft client) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String currentDim = CacheUtils.getDimensionId(mc);
            if (!currentDim.equals(ChestCache.currentLoadedDimension)) {
                ChestCache.loadFromDatabase(mc, currentDim);
                AutoStash.cacheDirty = true;
            }
            // Xử lý manual cache update cho AutoStash (luôn chạy ngầm)
            AutoStash.tickManualCacheUpdate();

            // Update Active Cache based on player movement and range changes
            AutoStash autoStash = getModule(AutoStash.class);
            if (autoStash != null) {
                autoStash.forceRefreshActiveCache();
            }

            List<Module> toggledModules = new ArrayList<>();
            for (Module module : moduleMap.values()) {
                if (module.isHold()) {
                    boolean isKeyDown = module.getKeyMapping().isDown();
                    if (module.isEnabled() != isKeyDown) {
                        module.setEnabled(isKeyDown);
                    }
                    while (module.getKeyMapping().consumeClick()) {}
                } else {
                    // Xử lý click keybind
                    while (module.getKeyMapping().consumeClick()) {
                        if (module instanceof StorageManager sm) {
                            // FIX: Nếu là StorageManager, bấm phím sẽ luôn mở GUI
                            if (!sm.isEnabled()) {
                                sm.setEnabled(true);
                                toggledModules.add(sm);
                            } else {
                                // Nếu module đã bật sẵn, chỉ việc mở lại GUI mà không toggle trạng thái
                                mc.setScreen(new StorageScreen(sm));
                            }
                        } else {
                            // Các module khác vẫn toggle bật/tắt bình thường
                            module.setEnabled(!module.isEnabled());
                            toggledModules.add(module);
                        }
                    }
                }
            }

            // Hiển thị thông báo overlay khi trạng thái module thay đổi
            if (!toggledModules.isEmpty()) {
                MutableComponent message = Component.empty();
                for (int i = 0; i < toggledModules.size(); i++) {
                    Module m = toggledModules.get(i);
                    if (i > 0) message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));

                    message.append(Component.literal(m.getName())
                            .withStyle(m.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                mc.gui.setOverlayMessage(message, false);
            }

            // Chạy logic tick cho các module đang bật
            moduleMap.values().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }

    /**
     * Initializes the module manager.
     * KHÔNG register event nữa (Fabric không dùng NeoForge.EVENT_BUS).
     * Chỉ load config.
     */
    public void init() {
        // Note: Load config AFTER modules are registered externally
        ConfigManager.getInstance().load();
    }
}