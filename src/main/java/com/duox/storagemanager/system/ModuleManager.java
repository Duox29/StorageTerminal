package com.duox.storagemanager.system;

import com.duox.storagemanager.gui.StorageScreen;
import com.duox.storagemanager.modules.*;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ManualContainerTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* Registers modules and dispatches client ticks. */
public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    private final Map<Class<? extends Module>, Module> moduleMap = new LinkedHashMap<>();
    private final List<Module> loadedModules;

    private static final int ACTIVE_CACHE_REFRESH_TICKS = 10;
    private int activeCacheRefreshTimer = 0;

    private ModuleManager() {
        register(new StorageManager());
        register(new AutoStash());
        // Registration only happens here, so the cached list never goes stale.
        loadedModules = List.copyOf(moduleMap.values());
    }

    public void register(Module module) {
        moduleMap.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleMap.get(clazz);
    }

    public List<Module> getModules() {
        return loadedModules;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean worldChanged = ChestCache.ensureLoaded(mc);
            if (worldChanged) {
                ManualContainerTracker.reset();
                moduleMap.values().forEach(Module::onWorldChanged);
            }
            ManualContainerTracker.tickManualCacheUpdate();

            // Refresh the active cache periodically instead of every tick.
            AutoStash autoStash = getModule(AutoStash.class);
            if (autoStash != null && ++activeCacheRefreshTimer >= ACTIVE_CACHE_REFRESH_TICKS) {
                activeCacheRefreshTimer = 0;
                autoStash.forceRefreshActiveCache();
            }

            List<Module> toggledModules = new ArrayList<>();
            for (Module module : moduleMap.values()) {
                while (module.getKeyMapping().consumeClick()) {
                    if (module instanceof StorageManager sm) {
                        // StorageManager's key opens the GUI instead of toggling.
                        if (!sm.isEnabled()) {
                            sm.setEnabled(true);
                            toggledModules.add(sm);
                        } else {
                            mc.gui.setScreen(new StorageScreen(sm));
                        }
                    } else {
                        module.setEnabled(!module.isEnabled());
                        toggledModules.add(module);
                    }
                }
            }

            if (!toggledModules.isEmpty()) {
                MutableComponent message = Component.empty();
                for (int i = 0; i < toggledModules.size(); i++) {
                    Module m = toggledModules.get(i);
                    if (i > 0) message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));

                    message.append(Component.literal(m.getName())
                            .withStyle(m.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                mc.gui.hud.setOverlayMessage(message, false);
            }

            moduleMap.values().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }

    public void init() {
        NeoForge.EVENT_BUS.register(this);
        // Modules must be registered before loading their config.
        ConfigManager.getInstance().load();
    }
}
