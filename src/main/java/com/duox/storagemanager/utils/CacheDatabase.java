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

/* SQLite storage for the AutoStash chest cache. */
public class CacheDatabase {
    private static final Logger LOGGER = LogManager.getLogger();

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
    private static final String SQL_DELETE_BY_POS =
            "DELETE FROM chest_cache WHERE server_id = ? AND dimension = ? AND pos = ?";
    private static final String SQL_INSERT =
            "INSERT INTO chest_cache (server_id, dimension, pos, item_id, count) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_BY_SERVER =
            "DELETE FROM chest_cache WHERE server_id = ?";

    private static CacheDatabase instance;

    private final Path dbPath;
    private final String serverId;
    private Connection connection;

    private CacheDatabase(Minecraft mc, String serverId) {
        this.serverId = serverId;
        this.dbPath = CacheUtils.getCacheDbPath(mc, "autostash");
        init();
    }

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
            // Close the read transaction started by autoCommit = false.
            connection.commit();
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

            // PRAGMAs must run outside a transaction.
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(PRAGMA_WAL);
                stmt.execute(PRAGMA_SYNC);
                stmt.execute(SQL_CREATE_TABLE);
            }

            // Each write is one explicit transaction.
            connection.setAutoCommit(false);
        } catch (SQLException | IOException e) {
            LOGGER.error("CacheDatabase: failed to initialize at {}", dbPath, e);
            close(); // Clean up after a partial initialization.
            connection = null;
        }
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void upsertChest(String dimensionId, String pos, Map<String, Integer> contents) {
        if (!isConnected()) return;

        try {
            // Replace this chest's previous contents.
            try (PreparedStatement del = connection.prepareStatement(SQL_DELETE_BY_POS)) {
                del.setString(1, serverId);
                del.setString(2, dimensionId);
                del.setString(3, pos);
                del.executeUpdate();
            }

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

    public synchronized void clearServer() {
        if (!isConnected()) return;
        try (PreparedStatement del = connection.prepareStatement(SQL_DELETE_BY_SERVER)) {
            del.setString(1, serverId);
            del.executeUpdate();
            connection.commit();
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

}