package com.mc1510ty.BlockLog.paperfolia.v26_1_2;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Database {

    private final Main plugin;
    private Connection connection;

    public Database(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // 接続と初期設定（WALモードで爆速化）
            String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/blocklog.db";
            connection = DriverManager.getConnection(url);

            try (Statement s = connection.createStatement()) {
                // WALモード: 書き込み中も読み込み（検索）をブロックしない
                s.execute("PRAGMA journal_mode = WAL;");
                s.execute("PRAGMA synchronous = NORMAL;");

                // テーブル作成
                s.execute("CREATE TABLE IF NOT EXISTS block_logs ( id INTEGER PRIMARY KEY AUTOINCREMENT, time DATETIME DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), uuid TEXT, world TEXT, action TINYINT, block TEXT, block_data TEXT, x INT, y INT, z INT );");

                // 検索用インデックス（座標検索を0msにする魔法）
                s.execute("CREATE INDEX IF NOT EXISTS idx_coords ON block_logs (world, x, y, z);");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite初期化失敗", e);
        }
    }

    public void log(String uuid, String world, int action, String block, String data, int x, int y, int z) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO block_logs (uuid, world, action, block, block_data, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid);
                ps.setString(2, world);
                ps.setInt(3, action);
                ps.setString(4, block);
                ps.setString(5, data);
                ps.setInt(6, x);
                ps.setInt(7, y);
                ps.setInt(8, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                // SQLITE_BUSY などの対策として、エラーログをしっかり出す
                plugin.getLogger().log(Level.WARNING, "Database insert failed", e);
            }
        });
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public CompletableFuture<List<String>> getLogs(String world, int x, int y, int z, int[] actions, int page) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> logs = new ArrayList<>();
            int offset = page * 5;
            String placeholders = java.util.stream.IntStream.range(0, actions.length)
                    .mapToObj(i -> "?").collect(java.util.stream.Collectors.joining(","));

            // OFFSETを追加して続きを取得
            String sql = "SELECT time, uuid, action, block FROM block_logs WHERE world = ? AND x = ? AND y = ? AND z = ? AND action IN (" + placeholders + ") ORDER BY id DESC LIMIT 5 OFFSET ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                for (int i = 0; i < actions.length; i++) ps.setInt(5 + i, actions[i]);
                ps.setInt(5 + actions.length, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String time = rs.getString("time");
                        String uuid = rs.getString("uuid");
                        int act = rs.getInt("action");
                        String block = rs.getString("block").replace("minecraft:", "");

                        String name = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid)).getName();
                        String actName = switch (act) {
                            case 0 -> "[破壊]";
                            case 1 -> "[設置]";
                            case 4 -> "[操作]";
                            default -> "[不明]";
                        };

                        logs.add(String.format("[%s] %s: %s %s", time, (name != null ? name : "不明"), actName, block));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Query failed: " + e.getMessage());
            }
            return logs;
        });
    }



}
