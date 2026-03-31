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
    private static final int BATCH_SIZE = 1000; // Prevent memory issues with huge datasets

    // SQL Constants - avoid string reconstruction on every call
    private static final String PRAGMA_WAL = "PRAGMA journal_mode=WAL;";
    private static final String PRAGMA_SYNC = "PRAGMA synchronous=NORMAL;";
    private static final String SQL_CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS chest_cache (
            server_id TEXT NOT NULL,
            dimension TEXT NOT NULL,
            pos TEXT NOT NULL,
            item_id TEXT NOT NULL,
            count INTEGER NOT NULL,
            PRIMARY KEY (server_id, dimension, pos, item_id)
        );
        """;
    private static final String SQL_SELECT_BY_DIM =
            "SELECT pos, item_id, count FROM chest_cache WHERE server_id = ? AND dimension = ?";
    private static final String SQL_SELECT_ALL =
            "SELECT pos, item_id, count FROM chest_cache WHERE server_id = ?";
    private static final String SQL_DELETE_BY_POS =
            "DELETE FROM chest_cache WHERE server_id = ? AND dimension = ? AND pos = ?";
    private static final String SQL_INSERT =
            "INSERT INTO chest_cache (server_id, dimension, pos, item_id, count) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_BY_SERVER =
            "DELETE FROM chest_cache WHERE server_id = ?";
    private static final String SQL_DELETE_BY_DIMENSION =
            "DELETE FROM chest_cache WHERE server_id = ? AND dimension = ?";

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

    public synchronized Map<String, Map<String, Integer>> loadByDimension(String dimensionId) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        if (!isConnected()) return result;

        try (PreparedStatement ps = connection.prepareStatement(SQL_SELECT_BY_DIM)) {
            ps.setString(1, serverId);
            ps.setString(2, dimensionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pos = rs.getString(1);
                    String item = rs.getString(2);
                    int count = rs.getInt(3);
                    result.computeIfAbsent(pos, k -> new HashMap<>()).put(item, count);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CacheDatabase: failed to load cache for dimension {}", dimensionId, e);
        }
        return result;
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
                stmt.execute(PRAGMA_WAL);
                stmt.execute(PRAGMA_SYNC);
            }

            ensureSchema();
        } catch (SQLException | IOException e) {
            LOGGER.error("CacheDatabase: failed to initialize at {}", dbPath, e);
            close(); // Ensure cleanup on partial failure
            connection = null;
        }
    }

    private void ensureSchema() throws SQLException {
        if (!isConnected()) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(SQL_CREATE_TABLE);
        }
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized Map<String, Map<String, Integer>> loadAll() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        if (!isConnected()) return result;

        try (PreparedStatement ps = connection.prepareStatement(SQL_SELECT_ALL)) {
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

    public synchronized void upsertChest(String dimensionId, String pos, Map<String, Integer> contents) {
        if (!isConnected()) return;

        try {
            connection.setAutoCommit(false);

            // Xóa dữ liệu cũ của rương này tại dimension cụ thể
            try (PreparedStatement del = connection.prepareStatement(SQL_DELETE_BY_POS)) {
                del.setString(1, serverId);
                del.setString(2, dimensionId);
                del.setString(3, pos);
                del.executeUpdate();
            }

            // Chèn dữ liệu mới
            if (!contents.isEmpty()) {
                try (PreparedStatement ins = connection.prepareStatement(SQL_INSERT)) {
                    for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                        ins.setString(1, serverId);
                        ins.setString(2, dimensionId);
                        ins.setString(3, pos);
                        ins.setString(4, entry.getKey());
                        ins.setInt(5, entry.getValue());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            LOGGER.error("CacheDatabase: failed to upsert chest {} in {}", pos, dimensionId, e);
        }
    }

    public synchronized void replaceAll(Map<String, Map<String, Integer>> data) {
        if (!isConnected()) return;
        String currentDim = CacheUtils.getDimensionId(Minecraft.getInstance()); // Lấy dim hiện tại
        if (data.isEmpty()) {
            clearServer();
            return;
        }

        boolean autoCommitOriginal = false;
        try {
            autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);

            // Clear all existing entries for this server
            try (PreparedStatement del = connection.prepareStatement(SQL_DELETE_BY_DIMENSION)) {
                del.setString(1, serverId);
                del.setString(2, currentDim); // Xóa chính xác rương ở dimension hiện tại
                del.executeUpdate();
            }

            // Insert new data with batch size control to prevent OOM
            try (PreparedStatement ins = connection.prepareStatement(SQL_INSERT)) {
                int batchCount = 0;
                for (Map.Entry<String, Map<String, Integer>> chest : data.entrySet()) {
                    String pos = chest.getKey();
                    for (Map.Entry<String, Integer> item : chest.getValue().entrySet()) {
                        ins.setString(1, serverId);
                        ins.setString(2, currentDim); // FIX: Thêm dimension vào đúng cột thứ 2
                        ins.setString(3, pos);         // Tọa độ sang cột 3
                        ins.setString(4, item.getKey());// Item ID sang cột 4
                        ins.setInt(5, item.getValue()); // Count sang cột 5
                        ins.addBatch();

                        if (++batchCount % BATCH_SIZE == 0) {
                            ins.executeBatch();
                        }
                    }
                }
                if (batchCount % BATCH_SIZE != 0) {
                    ins.executeBatch();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            rollback();
            LOGGER.error("CacheDatabase: failed to replace all cache entries", e);
        } finally {
            restoreAutoCommit(autoCommitOriginal);
        }
    }

    public synchronized void clearServer() {
        if (!isConnected()) return;
        try (PreparedStatement del = connection.prepareStatement(SQL_DELETE_BY_SERVER)) {
            del.setString(1, serverId);
            del.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("CacheDatabase: failed to clear cache for server {}", serverId, e);
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                LOGGER.warn("CacheDatabase: failed to close connection", e);
            }
            connection = null;
        }
    }

    private void rollback() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOGGER.error("CacheDatabase: rollback failed", e);
        }
    }

    private void restoreAutoCommit(boolean original) {
        try {
            if (isConnected()) {
                connection.setAutoCommit(original);
            }
        } catch (SQLException e) {
            LOGGER.warn("CacheDatabase: failed to restore auto-commit", e);
        }
    }
}