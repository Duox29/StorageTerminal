package com.duox.storagemanager.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Identifies supported Sophisticated Storage objects without requiring the mod at runtime. */
public final class SophisticatedStorageCompat {
    private static final String MOD_ID = "sophisticatedstorage";
    private static final String MENU_PACKAGE = "net.p3pp3rf1y.sophisticatedstorage.common.gui.";

    private SophisticatedStorageCompat() {}

    public static boolean isStorage(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id != null && id.getNamespace().equals(MOD_ID)
                && switch (id.getPath()) {
                    case "chest", "barrel", "limited_barrel", "shulker_box" -> true;
                    default -> false;
                };
    }

    public static boolean isStorageMenuType(MenuType<?> menuType) {
        var id = BuiltInRegistries.MENU.getKey(menuType);
        return id != null && id.getNamespace().equals(MOD_ID)
                && (id.getPath().equals("storage") || id.getPath().equals("limited_barrel"));
    }

    public static boolean isStorageMenu(AbstractContainerMenu menu) {
        String name = menu.getClass().getName();
        return name.equals(MENU_PACKAGE + "StorageContainerMenu")
                || name.equals(MENU_PACKAGE + "LimitedBarrelContainerMenu");
    }
}
