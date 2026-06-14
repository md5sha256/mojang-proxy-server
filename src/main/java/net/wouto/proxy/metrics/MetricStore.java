package net.wouto.proxy.metrics;

import java.util.Map;

/**
 * Persistent backing store for the runtime counters held by {@link MetricsService}.
 * <p>
 * Storage is a time series: each flush appends one timestamped row per counter, so
 * history is retained for graphing while {@link #loadLatest()} reconstructs the
 * current totals on startup. The engine (currently SQLite) is hidden behind this
 * interface so an alternative backend can be dropped in without touching the
 * service, mirroring {@link net.wouto.proxy.cache.store.ProfileStore}.
 */
public interface MetricStore {

    /**
     * Appends a timestamped snapshot row for every counter in {@code values}.
     *
     * @param ts     epoch-millis instant the snapshot was taken
     * @param values counter name to cumulative value
     */
    void saveSnapshot(long ts, Map<String, Long> values);

    /**
     * Returns the most recently persisted value for each counter name, used to seed
     * the in-memory counters so totals stay continuous across restarts.
     *
     * @return counter name to its latest value (empty if nothing has been persisted)
     */
    Map<String, Long> loadLatest();

    /**
     * Deletes snapshot rows recorded strictly before {@code olderThan} (epoch millis).
     *
     * @return number of rows removed
     */
    int deleteSnapshotsBefore(long olderThan);
}
