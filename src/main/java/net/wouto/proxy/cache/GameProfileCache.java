package net.wouto.proxy.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.wouto.proxy.Config;
import net.wouto.proxy.Util;
import net.wouto.proxy.response.result.HasJoinedMinecraftServerResponseImpl;
import net.wouto.proxy.response.result.MinecraftProfilePropertiesResponseImpl;
import net.wouto.proxy.response.result.ProfileSearchResultsResponseImpl;
import net.wouto.proxy.cache.store.ProfileStore;
import net.wouto.proxy.service.MojangAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GameProfileCache {

    private static final Logger log = LoggerFactory.getLogger(GameProfileCache.class);

    private Cache<String, GameProfile> nameProfileCache;
    private Cache<UUID, GameProfile> uuidProfileCache;

    private final long ttlMillis;

    /**
     * Optional L2 persistent store sitting behind the in-memory L1 caches above.
     * Null until wired up after the Spring context starts (see
     * {@link CachePersistenceConfig}); when null this behaves as a pure in-memory cache.
     */
    private volatile ProfileStore profileStore;

    public GameProfileCache(Config config) {
        long cacheCount = Long.parseLong(config.getProperty("cacheCount", "10000"));
        long cacheDurationSeconds = Long.parseLong(config.getProperty("cacheDuration", "3600"));
        this.ttlMillis = TimeUnit.SECONDS.toMillis(cacheDurationSeconds);

        this.nameProfileCache = CacheBuilder.newBuilder()
                .maximumSize(cacheCount)
                .expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS)
                .build();

        this.uuidProfileCache = CacheBuilder.newBuilder()
                .maximumSize(cacheCount)
                .expireAfterWrite(cacheDurationSeconds, TimeUnit.SECONDS)
                .build();
    }

    /** Wires the L2 persistent store. Called once after the Spring context is up. */
    public void setProfileStore(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    public MinecraftProfilePropertiesResponseImpl fillGameProfile(String uuid, boolean unsigned) {
        UUID uuidObj = null;
        try {
            uuidObj = Util.deserialize(uuid);
        } catch (Exception ignored) {
        }
        if (uuidObj != null) {
            GameProfile cached = lookupByUuid(uuidObj);
            if (cached != null) {
                return new MinecraftProfilePropertiesResponseImpl(cached);
            }
        }
        MinecraftProfilePropertiesResponseImpl response = MojangAPI.getInstance().fillGameProfile(uuid, true);
        cache(response.getGameProfile());
        return response;
    }

    public ProfileSearchResultsResponseImpl findProfilesByNames(List<String> names) {
        ProfileSearchResultsResponseImpl response = new ProfileSearchResultsResponseImpl();
        List<GameProfile> profiles = new ArrayList<>();
        // Read-only against the cache: the name-search endpoint returns only id+name
        // (no properties), so caching its results would poison uuid lookups that
        // expect full profiles. We only serve names already cached as full profiles.
        names.removeIf(s -> {
            GameProfile profile = lookupByName(s);
            if (profile != null) {
                profiles.add(profile);
                return true;
            }
            return false;
        });
        if (!names.isEmpty()) {
            ProfileSearchResultsResponseImpl res = MojangAPI.getInstance().findProfilesByNames(names);
            if (res != null && res.getProfiles() != null) {
                for (GameProfile profile : res.getProfiles()) {
                    if (profile != null) {
                        profiles.add(profile);
                    }
                }
            }
        }
        response.setProfiles(profiles.toArray(new GameProfile[profiles.size()]));
        if (log.isDebugEnabled()) {
            int length = (response.getProfiles() != null ? response.getProfiles().length : 0);
            log.debug("GameProfileCache's interpretation: profile count = {}", length);
            if (response.getProfiles() != null) {
                for (GameProfile gameProfile : response.getProfiles()) {
                    for (Property property : gameProfile.getProperties().values()) {
                        log.debug("\t{} = {}{}", property.getName(), property.getValue(),
                                property.hasSignature() ? " (signed: " + property.getSignature() + ")" : "");
                    }
                }
            }
        }
        return response;
    }

    public HasJoinedMinecraftServerResponseImpl hasJoined(String username, String serverId) throws Exception {
        HasJoinedMinecraftServerResponseImpl response = null;
        try {
            response = MojangAPI.getInstance().hasJoined(username, serverId);
            GameProfile gameProfile = new GameProfile(response.getId(), response.getName());
            gameProfile.getProperties().putAll(response.getPropertyMap());
            cache(gameProfile);
        } catch (Exception e) {
            log.warn("hasJoined failed for {}, falling back to cache", username, e);
            GameProfile profile = lookupByName(username);
            if (profile != null) {
                response = new HasJoinedMinecraftServerResponseImpl(profile.getId(), profile.getName(), profile.getProperties());
            }
        }
        return response;
    }

    /** Looks up a profile by UUID through L1, then L2; warms L1 on an L2 hit. */
    private GameProfile lookupByUuid(UUID uuid) {
        GameProfile profile = this.uuidProfileCache.getIfPresent(uuid);
        if (profile != null) {
            return profile;
        }
        ProfileStore store = this.profileStore;
        if (store != null) {
            try {
                profile = store.findByUuid(uuid, minCachedAt()).orElse(null);
                if (profile != null) {
                    cacheLocally(profile);
                }
            } catch (Exception e) {
                log.warn("L2 lookup by uuid {} failed", uuid, e);
            }
        }
        return profile;
    }

    /** Looks up a profile by name through L1, then L2; warms L1 on an L2 hit. */
    private GameProfile lookupByName(String name) {
        GameProfile profile = this.nameProfileCache.getIfPresent(name);
        if (profile != null) {
            return profile;
        }
        ProfileStore store = this.profileStore;
        if (store != null) {
            try {
                profile = store.findByName(name, minCachedAt()).orElse(null);
                if (profile != null) {
                    cacheLocally(profile);
                }
            } catch (Exception e) {
                log.warn("L2 lookup by name {} failed", name, e);
            }
        }
        return profile;
    }

    /** Writes a full profile through to both L1 and the L2 store. */
    private void cache(GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null) {
            return;
        }
        cacheLocally(profile);
        ProfileStore store = this.profileStore;
        if (store != null) {
            try {
                store.save(profile, System.currentTimeMillis());
            } catch (Exception e) {
                log.warn("L2 save for {} failed", profile.getName(), e);
            }
        }
    }

    private void cacheLocally(GameProfile profile) {
        this.nameProfileCache.put(profile.getName(), profile);
        this.uuidProfileCache.put(profile.getId(), profile);
    }

    private long minCachedAt() {
        return System.currentTimeMillis() - this.ttlMillis;
    }

    /** Purges expired rows from the L2 store, if one is wired. Returns rows removed. */
    public int purgeExpiredPersistent() {
        ProfileStore store = this.profileStore;
        if (store == null) {
            return 0;
        }
        return store.deleteExpired(minCachedAt());
    }

}
