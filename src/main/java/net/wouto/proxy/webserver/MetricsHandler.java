package net.wouto.proxy.webserver;

import net.wouto.proxy.MojangProxyServer;
import net.wouto.proxy.metrics.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves a JSON snapshot of the runtime counters maintained by
 * {@link MetricsService}. Values are cumulative (seeded from the persisted snapshot
 * history on startup) and grouped by their dotted-name prefix, so {@code cache.l1_hit}
 * is rendered as {@code {"cache": {"l1_hit": N}}}.
 * <p>
 * As a specific mapping this takes precedence over the {@code /**} catch-all in
 * {@link UnknownRestHandler}.
 */
@RestController
public class MetricsHandler {

    private final MetricsService metrics;

    public MetricsHandler(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    @ResponseBody
    public Map<String, Object> metrics(@RequestParam(value = "proxyKey", required = false) String key) throws Exception {
        MojangProxyServer.authorize(key);
        return nest(metrics.snapshot());
    }

    /** Turns a flat dotted-name map into a two-level nested map for readable JSON. */
    private static Map<String, Object> nest(Map<String, Long> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : flat.entrySet()) {
            String name = e.getKey();
            int dot = name.indexOf('.');
            if (dot < 0) {
                root.put(name, e.getValue());
                continue;
            }
            String group = name.substring(0, dot);
            String leaf = name.substring(dot + 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> bucket =
                    (Map<String, Object>) root.computeIfAbsent(group, g -> new LinkedHashMap<String, Object>());
            bucket.put(leaf, e.getValue());
        }
        return root;
    }
}
