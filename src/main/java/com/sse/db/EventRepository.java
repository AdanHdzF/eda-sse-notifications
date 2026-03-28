package com.sse.db;

import com.sse.model.ClientEvent;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventRepository {
    private final DataSource dataSource;

    public EventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Long> insertBatch(List<ClientEvent> events) throws SQLException {
        String sql = "INSERT INTO client_events (client_id, event_type, payload) VALUES (?, ?, ?)";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (ClientEvent event : events) {
                ps.setString(1, event.clientId());
                ps.setString(2, event.eventType());
                ps.setString(3, event.payload());
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    public List<ClientEvent> replayAfter(String clientId, long afterId, int limit) throws SQLException {
        String sql = "SELECT id, client_id, event_type, payload, created_at " +
                     "FROM client_events WHERE client_id = ? AND id > ? ORDER BY id LIMIT ?";
        List<ClientEvent> events = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setLong(2, afterId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new ClientEvent(
                        rs.getLong("id"),
                        rs.getString("client_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        }
        return events;
    }

    public int purgeOlderThan24h(int batchSize) throws SQLException {
        String sql = "DELETE FROM client_events WHERE created_at < NOW() - INTERVAL 24 HOUR LIMIT ?";
        int totalDeleted = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted;
            do {
                ps.setInt(1, batchSize);
                deleted = ps.executeUpdate();
                totalDeleted += deleted;
            } while (deleted == batchSize);
        }
        return totalDeleted;
    }
}
