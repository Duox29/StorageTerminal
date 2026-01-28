package com.duox.storagemanager.utils;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight SQLite helper dedicated to the AutoStash cache.
 * Persists chest contents in a transactional store to avoid JSON corruption on crash.
 */
public class CacheDatabase {
    private static final Logger LOGGER = LogManager.getLogger();
    private static CacheDatabase instance;

    private final Path dbPath;
    private final String serverId;
    private Connection connection;

    private CacheDatabase(Minecraft mc, String serverId) {
        this.serverId = serverId;
        this.dbPath = CacheUtils.getCacheDbPath(mc, "autostash");
        init();
    }

    /**
    * Get singleton per server/world. Switches DB when serverId changes.
    */
    public static synchronized CacheDatabase getInstance(Minecraft mc) {
        String safeServerId = CacheUtils.getSafeServerIdentifier(mc);
        if (instance == null || !instance.serverId.equals(safeServerId)) {
            if (instance != null) {
                instance.close();
            }
            instance = new CacheDatabase(mc, safeServerId);
        }
        return instance;
    }

    private void init() {
        try {
            if (!Files.exists(dbPath.getParent())) {
                Files.createDirectories(dbPath.getParent());
            }
            String url = "jdbc:sqlite:" + dbPath.toString();
            connection = DriverManager.getConnection(url);
            connection.setAutoCommit(true);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }

            ensureSchema();
        } catch (SQLException | IOException e) {
            LOGGER.error("CacheDatabase: failed to initialize at {}", dbPath, e);
            connection = null;
        }
    }

    private void ensureSchema() throws SQLException {
        if (connection == null) return;
        String sql = """
                CREATE TABLE IF NOT EXISTS chest_cache (
                    server_id TEXT NOT NULL,
                    pos TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    PRIMARY KEY (server_id, pos, item_id)
                );
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public synchronized Map<String, Map<String, Integer>> loadAll() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        if (connection == null) return result;

        String sql = "SELECT pos, item_id, count FROM chest_cache WHERE server_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pos = rs.getString(1);
                    String item = rs.getString(2);
                    int count = rs.getInt(3);
                    result.computeIfAbsent(pos, k -> new HashMap<>())
                            .put(item, count);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CacheDatabase: failed to load cache", e);
        }
        return result;
    }

    public synchronized void upsertChest(String pos, Map<String, Integer> contents) {
        if (connection == null) return;
        String deleteSql = "DELETE FROM chest_cache WHERE server_id = ? AND pos = ?";
        String insertSql = "INSERT INTO chest_cache (server_id, pos, item_id, count) VALUES (?, ?, ?, ?)";

        try (PreparedStatement del = connection.prepareStatement(deleteSql);
             PreparedStatement ins = connection.prepareStatement(insertSql)) {
            connection.setAutoCommit(false);

            del.setString(1, serverId);
            del.setString(2, pos);
            del.executeUpdate();

            for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                ins.setString(1, serverId);
                ins.setString(2, pos);
                ins.setString(3, entry.getKey());
                ins.setInt(4, entry.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            LOGGER.error("CacheDatabase: failed to upsert chest {}", pos, e);
        } finally {
            try { if (connection != null) connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized void replaceAll(Map<String, Map<String, Integer>> data) {
        if (connection == null) return;
        String deleteSql = "DELETE FROM chest_cache WHERE server_id = ?";
        String insertSql = "INSERT INTO chest_cache (server_id, pos, item_id, count) VALUES (?, ?, ?, ?)";

        try (PreparedStatement del = connection.prepareStatement(deleteSql);
             PreparedStatement ins = connection.prepareStatement(insertSql)) {
            connection.setAutoCommit(false);

            del.setString(1, serverId);
            del.executeUpdate();

            for (Map.Entry<String, Map<String, Integer>> chest : data.entrySet()) {
                String pos = chest.getKey();
                for (Map.Entry<String, Integer> item : chest.getValue().entrySet()) {
                    ins.setString(1, serverId);
                    ins.setString(2, pos);
                    ins.setString(3, item.getKey());
                    ins.setInt(4, item.getValue());
                    ins.addBatch();
                }
            }
            ins.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            LOGGER.error("CacheDatabase: failed to replace all cache entries", e);
        } finally {
            try { if (connection != null) connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized void clearServer() {
        if (connection == null) return;
        String deleteSql = "DELETE FROM chest_cache WHERE server_id = ?";
        try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
            del.setString(1, serverId);
            del.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("CacheDatabase: failed to clear cache for server {}", serverId, e);
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.warn("CacheDatabase: failed to close connection", e);
            }
            connection = null;
        }
    }
}
