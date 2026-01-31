/* ==========================================================
File:        LocalDatabase.java
Description: Manages local SQLite database for offline heartbeat storage.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;

public class LocalDatabase {
    private static final String DB_NAME = "wakatime_heartbeats.db";
    private static String dbPath = null;
    private static Connection connection = null;

    /**
     * Get the path to the Developer folder where the database should be stored
     */
    private static String getDeveloperFolderPath() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        
        File devFolder;
        if (os.contains("win")) {
            // Windows: C:\Users\<username>\Developer
            devFolder = new File(userHome, "Developer");
        } else if (os.contains("mac")) {
            // macOS: /Users/<username>/Developer
            devFolder = new File(userHome, "Developer");
        } else {
            // Linux: /home/<username>/Developer
            devFolder = new File(userHome, "Developer");
        }
        
        // Create Developer folder if it doesn't exist
        if (!devFolder.exists()) {
            devFolder.mkdirs();
        }
        
        return devFolder.getAbsolutePath();
    }

    /**
     * Get the full path to the database file
     */
    private static String getDbPath() {
        if (dbPath == null) {
            dbPath = new File(getDeveloperFolderPath(), DB_NAME).getAbsolutePath();
            WakaTime.log.info("WakaTime database path: " + dbPath);
        }
        return dbPath;
    }

    /**
     * Initialize the database connection and create tables if needed
     */
    public static synchronized void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            String path = getDbPath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            
            // Create table if it doesn't exist
            createTable();
            
            WakaTime.log.info("Local database initialized successfully");
        } catch (ClassNotFoundException e) {
            WakaTime.log.error("SQLite JDBC driver not found. Please ensure sqlite-jdbc is in classpath.", e);
        } catch (SQLException e) {
            WakaTime.log.error("Failed to initialize local database", e);
        }
    }

    /**
     * Create the heartbeats table
     */
    private static void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS heartbeats ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "entity TEXT NOT NULL,"
                + "timestamp REAL NOT NULL,"
                + "is_write INTEGER NOT NULL,"
                + "is_unsaved_file INTEGER NOT NULL,"
                + "is_building INTEGER NOT NULL,"
                + "project TEXT,"
                + "language TEXT,"
                + "line_count INTEGER,"
                + "line_number INTEGER,"
                + "cursor_position INTEGER,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";
        
        Statement stmt = connection.createStatement();
        stmt.execute(sql);
        stmt.close();
        
        // Create index on timestamp for faster queries
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_timestamp ON heartbeats(timestamp)";
        stmt = connection.createStatement();
        stmt.execute(indexSql);
        stmt.close();
    }

    /**
     * Insert a heartbeat into the database
     */
    public static synchronized void insertHeartbeat(Heartbeat heartbeat) {
        if (connection == null) {
            initialize();
        }
        
        if (connection == null) {
            WakaTime.log.warn("Cannot insert heartbeat: database not initialized");
            return;
        }
        
        String sql = "INSERT INTO heartbeats (entity, timestamp, is_write, is_unsaved_file, is_building, "
                + "project, language, line_count, line_number, cursor_position) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, heartbeat.entity);
            pstmt.setDouble(2, heartbeat.timestamp.doubleValue());
            pstmt.setInt(3, heartbeat.isWrite ? 1 : 0);
            pstmt.setInt(4, heartbeat.isUnsavedFile ? 1 : 0);
            pstmt.setInt(5, heartbeat.isBuilding ? 1 : 0);
            pstmt.setString(6, heartbeat.project);
            pstmt.setString(7, heartbeat.language);
            pstmt.setObject(8, heartbeat.lineCount);
            pstmt.setObject(9, heartbeat.lineNumber);
            pstmt.setObject(10, heartbeat.cursorPosition);
            
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            WakaTime.log.error("Failed to insert heartbeat into database", e);
        }
    }

    /**
     * Get all heartbeats from the database
     */
    public static synchronized ArrayList<Heartbeat> getAllHeartbeats() {
        ArrayList<Heartbeat> heartbeats = new ArrayList<>();
        
        if (connection == null) {
            initialize();
        }
        
        if (connection == null) {
            WakaTime.log.warn("Cannot get heartbeats: database not initialized");
            return heartbeats;
        }
        
        String sql = "SELECT * FROM heartbeats ORDER BY timestamp ASC";
        
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                Heartbeat h = new Heartbeat();
                h.entity = rs.getString("entity");
                h.timestamp = new BigDecimal(rs.getDouble("timestamp"));
                h.isWrite = rs.getInt("is_write") == 1;
                h.isUnsavedFile = rs.getInt("is_unsaved_file") == 1;
                h.isBuilding = rs.getInt("is_building") == 1;
                h.project = rs.getString("project");
                h.language = rs.getString("language");
                
                int lineCount = rs.getInt("line_count");
                if (!rs.wasNull()) {
                    h.lineCount = lineCount;
                }
                
                int lineNumber = rs.getInt("line_number");
                if (!rs.wasNull()) {
                    h.lineNumber = lineNumber;
                }
                
                int cursorPosition = rs.getInt("cursor_position");
                if (!rs.wasNull()) {
                    h.cursorPosition = cursorPosition;
                }
                
                heartbeats.add(h);
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            WakaTime.log.error("Failed to get heartbeats from database", e);
        }
        
        return heartbeats;
    }

    /**
     * Get count of stored heartbeats
     */
    public static synchronized int getHeartbeatCount() {
        if (connection == null) {
            initialize();
        }
        
        if (connection == null) {
            return 0;
        }
        
        String sql = "SELECT COUNT(*) FROM heartbeats";
        
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            if (rs.next()) {
                int count = rs.getInt(1);
                rs.close();
                stmt.close();
                return count;
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            WakaTime.log.error("Failed to get heartbeat count", e);
        }
        
        return 0;
    }

    /**
     * Clear all heartbeats from the database
     */
    public static synchronized void clearAllHeartbeats() {
        if (connection == null) {
            initialize();
        }
        
        if (connection == null) {
            WakaTime.log.warn("Cannot clear heartbeats: database not initialized");
            return;
        }
        
        String sql = "DELETE FROM heartbeats";
        
        try {
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            stmt.close();
            WakaTime.log.info("Cleared all heartbeats from local database");
        } catch (SQLException e) {
            WakaTime.log.error("Failed to clear heartbeats from database", e);
        }
    }

    /**
     * Close the database connection
     */
    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                WakaTime.log.info("Local database connection closed");
            } catch (SQLException e) {
                WakaTime.log.error("Failed to close database connection", e);
            }
        }
    }
}
