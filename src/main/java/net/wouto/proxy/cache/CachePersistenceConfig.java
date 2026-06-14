package net.wouto.proxy.cache;

import jakarta.annotation.PostConstruct;
import net.wouto.proxy.MojangProxyServer;
import net.wouto.proxy.cache.store.ProfileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bridges the Spring-managed persistence layer onto the {@link GameProfileCache},
 * which is constructed outside the Spring context (via reflection in
 * {@link MojangProxyServer}). Rather than turn the cache into a bean, we inject the
 * {@link ProfileStore} into the already-built instance once the context is up, and
 * run periodic expiry cleanup against it.
 */
@Configuration
@EnableScheduling
public class CachePersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(CachePersistenceConfig.class);

    private final ProfileStore profileStore;

    public CachePersistenceConfig(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    @PostConstruct
    public void attachStore() {
        GameProfileCache cache = MojangProxyServer.get().getGameProfileCache();
        if (cache != null) {
            cache.setProfileStore(profileStore);
            log.info("Persistent L2 profile store attached to GameProfileCache");
        } else {
            log.warn("No GameProfileCache available; L2 persistent store not attached");
        }
    }

    @Scheduled(fixedDelayString = "${cache.cleanup-interval-ms:3600000}")
    public void purgeExpired() {
        GameProfileCache cache = MojangProxyServer.get().getGameProfileCache();
        if (cache == null) {
            return;
        }
        int removed = cache.purgeExpiredPersistent();
        if (removed > 0) {
            log.debug("Purged {} expired profile(s) from L2 store", removed);
        }
    }
}
