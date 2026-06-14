package net.wouto.proxy.metrics;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight runtime metrics: contended-friendly {@link LongAdder} counters held
 * in memory and periodically flushed to a {@link MetricStore} as a time series.
 * <p>
 * Counters are seeded from the latest persisted snapshot on startup, so the totals
 * are cumulative across restarts and the persisted series stays monotonic. The hot
 * path only touches an in-memory adder; the DB is touched on a schedule, never per
 * request.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    // Cache outcomes — exactly one is recorded per profile lookup.
    public static final String CACHE_L1_HIT = "cache.l1_hit";
    public static final String CACHE_L2_HIT = "cache.l2_hit";
    public static final String CACHE_MISS = "cache.miss";

    // Per-endpoint request counts.
    public static final String ENDPOINT_FILL_GAME_PROFILE = "endpoint.fillGameProfile";
    public static final String ENDPOINT_HAS_JOINED = "endpoint.hasJoined";
    public static final String ENDPOINT_FIND_PROFILES_BY_NAMES = "endpoint.findProfilesByNames";
    public static final String ENDPOINT_PUBLIC_KEYS = "endpoint.publickeys";
    public static final String ENDPOINT_UNKNOWN = "endpoint.unknown";

    // Upstream Mojang traffic and how much of it failed.
    public static final String UPSTREAM_CALLS = "upstream.calls";
    public static final String UPSTREAM_ERRORS = "upstream.errors";

    private static final List<String> KNOWN_METRICS = List.of(
            CACHE_L1_HIT, CACHE_L2_HIT, CACHE_MISS,
            ENDPOINT_FILL_GAME_PROFILE, ENDPOINT_HAS_JOINED, ENDPOINT_FIND_PROFILES_BY_NAMES,
            ENDPOINT_PUBLIC_KEYS, ENDPOINT_UNKNOWN,
            UPSTREAM_CALLS, UPSTREAM_ERRORS);

    private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final MetricStore store;

    @Value("${metrics.retention-ms:604800000}")
    private long retentionMs;

    public MetricsService(MetricStore store) {
        this.store = store;
        // Pre-register every known counter so snapshots and the endpoint always
        // expose the full set, even before the first increment.
        for (String name : KNOWN_METRICS) {
            counters.put(name, new LongAdder());
        }
    }

    /** Seeds in-memory counters from the most recent persisted values. */
    @PostConstruct
    public void seedFromStore() {
        try {
            Map<String, Long> latest = store.loadLatest();
            latest.forEach((name, value) ->
                    counters.computeIfAbsent(name, n -> new LongAdder()).add(value));
            if (!latest.isEmpty()) {
                log.info("Seeded {} metric counter(s) from persisted snapshot", latest.size());
            }
        } catch (Exception e) {
            log.warn("Failed to seed metrics from store; starting counters at zero", e);
        }
    }

    public void increment(String name) {
        counters.computeIfAbsent(name, n -> new LongAdder()).increment();
    }

    /** Current cumulative value of every counter, as a flat dotted-name map. */
    public Map<String, Long> snapshot() {
        Map<String, Long> out = new LinkedHashMap<>();
        counters.forEach((name, adder) -> out.put(name, adder.sum()));
        return out;
    }

    @Scheduled(fixedDelayString = "${metrics.flush-interval-ms:60000}")
    public void flush() {
        try {
            store.saveSnapshot(System.currentTimeMillis(), snapshot());
        } catch (Exception e) {
            log.warn("Failed to persist metrics snapshot", e);
        }
    }

    @Scheduled(fixedDelayString = "${metrics.cleanup-interval-ms:3600000}")
    public void purgeOldSnapshots() {
        try {
            int removed = store.deleteSnapshotsBefore(System.currentTimeMillis() - retentionMs);
            if (removed > 0) {
                log.debug("Purged {} old metric snapshot row(s)", removed);
            }
        } catch (Exception e) {
            log.warn("Failed to purge old metric snapshots", e);
        }
    }
}
