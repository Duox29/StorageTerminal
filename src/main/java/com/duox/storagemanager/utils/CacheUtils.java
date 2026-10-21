package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

/* Cache path, position, and dimension helpers. */
public class CacheUtils {

    private CacheUtils() {}

    public static Path getCacheDbPath(Minecraft mc, String prefix) {
        String safeId = getSafeServerIdentifier(mc);
        return FMLPaths.CONFIGDIR.get().resolve(prefix + "_" + safeId + ".db");
    }

    public static String getSafeServerIdentifier(Minecraft mc) {
        String serverId = getServerIdentifier(mc);
        String readable = serverId.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (readable.length() > 80) {
            readable = readable.substring(0, 80);
        }
        // Keep distinct ids from collapsing to the same filename.
        return readable + "_" + shortHash(serverId);
    }

    private static String getServerIdentifier(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            Path worldPath = mc.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT)
                    .normalize();
            Path fileName = worldPath.getFileName();
            return "sp_" + (fileName == null ? worldPath : fileName.toString());
        }
        if (mc.getCurrentServer() != null) {
            return "mp_" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        }
        return "default";
    }

    public static String posToString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos stringToPos(String s) {
        String[] parts = s.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public static String getDimensionId(Minecraft mc) {
        if (mc.level == null) return "unknown";
        return mc.level.dimension().identifier().toString();
    }

    private static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // Stable enough for a rare digest fallback.
            return Integer.toUnsignedString(value.hashCode(), 16);
        }
    }
}
