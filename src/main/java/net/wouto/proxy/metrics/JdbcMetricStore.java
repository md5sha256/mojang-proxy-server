package net.wouto.proxy.metrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed {@link MetricStore}. Pure portable JDBC, so a different backend is
 * a matter of swapping this one class.
 */
@Repository
public class JdbcMetricStore implements MetricStore {

    private static final String INSERT =
            "INSERT INTO metric_snapshot (ts, name, value) VALUES (?, ?, ?)";

    // Latest value per counter: join each row against the max(ts) for its name.
    private static final String LATEST =
            "SELECT m.name AS name, m.value AS value FROM metric_snapshot m " +
            "JOIN (SELECT name, MAX(ts) AS mts FROM metric_snapshot GROUP BY name) latest " +
            "ON m.name = latest.name AND m.ts = latest.mts";

    private final JdbcTemplate jdbc;

    public JdbcMetricStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveSnapshot(long ts, Map<String, Long> values) {
        List<Object[]> batch = new ArrayList<>(values.size());
        for (Map.Entry<String, Long> e : values.entrySet()) {
            batch.add(new Object[]{ts, e.getKey(), e.getValue()});
        }
        jdbc.batchUpdate(INSERT, batch);
    }

    @Override
    public Map<String, Long> loadLatest() {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(LATEST, rs -> {
            out.put(rs.getString("name"), rs.getLong("value"));
        });
        return out;
    }

    @Override
    public int deleteSnapshotsBefore(long olderThan) {
        return jdbc.update("DELETE FROM metric_snapshot WHERE ts < ?", olderThan);
    }
}
