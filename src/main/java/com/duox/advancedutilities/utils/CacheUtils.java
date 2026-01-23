package com.duox.advancedutilities.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class handling JSON serialization and File I/O for caching mechanisms.
 * Designed to be stateless and generic.
 */
public class CacheUtils {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Private constructor to prevent instantiation
    private CacheUtils() {}

    /**
     * Generic method to save any object to a JSON file.
     */
    public static <T> void saveToJson(Path filePath, T data) {
        try {
            if (!Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }
            try (Writer writer = new FileWriter(filePath.toFile())) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("CacheUtils: Failed to save JSON to {}", filePath, e);
        }
    }

    /**
     * Generic method to load any object from a JSON file.
     * Returns null if file doesn't exist or error occurs.
     */
    public static <T> T loadFromJson(Path filePath, Type typeOfT) {
        if (!Files.exists(filePath)) return null;

        try (Reader reader = new FileReader(filePath.toFile())) {
            return GSON.fromJson(reader, typeOfT);
        } catch (IOException e) {
            LOGGER.error("CacheUtils: Failed to load JSON from {}", filePath, e);
            return null;
        }
    }

    /**
     * Generates a safe file path based on the current server/world context.
     */
    public static Path getCacheFilePath(Minecraft mc, String prefix) {
        String serverId = getServerIdentifier(mc);
        // Sanitize filename to prevent IO issues
        String safeId = serverId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return FMLPaths.CONFIGDIR.get().resolve(prefix + "_" + safeId + ".json");
    }

    private static String getServerIdentifier(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            return "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            return "mp_" + mc.getCurrentServer().ip.replaceAll("[:/]", "_");
        }
        return "default";
    }

    public static void updateSpecificJsonObject(Path filePath, String chestKey, Map<String, Integer> chestData) {
        // 1. Định nghĩa kiểu dữ liệu cho Gson
        Type type = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();

        // 2. Load map hiện tại từ file (đảm bảo không ghi đè mất dữ liệu của rương khác)
        // Nếu muốn tối ưu tốc độ, có thể truyền map từ memory vào thay vì load lại từ đĩa.
        Map<String, Map<String, Integer>> fullCache = loadFromJson(filePath, type);

        if (fullCache == null) {
            fullCache = new HashMap<>();
        }

        // 3. Cập nhật entry của rương này
        fullCache.put(chestKey, chestData);

        // 4. Lưu lại xuống đĩa
        saveToJson(filePath, fullCache);
    }
    public static String posToString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

   public static BlockPos stringToPos(String s) {
        String[] parts = s.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
