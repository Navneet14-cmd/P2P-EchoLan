package com.robustians.echolan.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.robustians.echolan.model.Message;

public class SqliteMessageDao implements MessageDao {
    private static final String DB_URL = "jdbc:sqlite:echolan.db";
    private Connection connection;

    public SqliteMessageDao() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to connect to SQLite database", e);
        }
    }

    @Override
    public void initSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "peer_id TEXT NOT NULL DEFAULT '', " +
                "type INTEGER NOT NULL, " +
                "content TEXT NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);

            // Add peer_id column if this is migration from older schema.
            boolean hasPeerId = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(messages)")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("peer_id".equalsIgnoreCase(name)) {
                        hasPeerId = true;
                        break;
                    }
                }
            }

            if (!hasPeerId) {
                stmt.execute("ALTER TABLE messages ADD COLUMN peer_id TEXT NOT NULL DEFAULT ''");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize message table", e);
        }
    }

    @Override
    public void insert(Message message) {
        String sql = "INSERT INTO messages(peer_id, type, content) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, message.getPeerId());
            pstmt.setInt(2, message.getType());
            pstmt.setString(3, message.getContent());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert message", e);
        }
    }

    @Override
    public List<Message> getRecent(String peerId, int limit) {
        String sql = "SELECT type, content, peer_id FROM messages WHERE peer_id = ? ORDER BY id DESC LIMIT ?";
        List<Message> list = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(rs.getInt("type"), rs.getString("content"), rs.getString("peer_id")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read recent messages", e);
        }

        // Reverse to chronological order
        List<Message> ordered = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            ordered.add(list.get(i));
        }
        return ordered;
    }

    @Override
    public int count(String peerId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE peer_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count messages", e);
        }
    }

    @Override
    public void deleteOldest(String peerId, int excess) {
        if (excess <= 0) {
            return;
        }

        String sql = "DELETE FROM messages WHERE id IN (" +
                "SELECT id FROM messages WHERE peer_id = ? ORDER BY id ASC LIMIT ?" +
                ")";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            pstmt.setInt(2, excess);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to trim oldest messages", e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
