package net.wouto.proxy.cache.store;

import com.mojang.authlib.GameProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistent L2 backing store for resolved {@link GameProfile}s, sitting behind
 * the in-memory L1 cache in {@link net.wouto.proxy.cache.GameProfileCache}.
 * <p>
 * Reads are TTL-filtered by the caller via {@code minCachedAt}: only rows written
 * at or after that instant are considered fresh. The engine (currently SQLite) is
 * intentionally hidden behind this interface so an alternative backend can be
 * dropped in without touching the cache.
 */
public interface ProfileStore {

    /**
     * Looks up a fresh profile by UUID.
     *
     * @param minCachedAt rows older than this epoch-millis instant are treated as expired
     */
    Optional<GameProfile> findByUuid(UUID uuid, long minCachedAt);

    /**
     * Looks up a fresh profile by (case-insensitive) name, returning the most
     * recently cached row when more than one matches.
     *
     * @param minCachedAt rows older than this epoch-millis instant are treated as expired
     */
    Optional<GameProfile> findByName(String name, long minCachedAt);

    /**
     * Inserts or updates the profile, stamping it with {@code cachedAt} (epoch millis).
     */
    void save(GameProfile profile, long cachedAt);

    /**
     * Deletes rows cached strictly before {@code olderThan} (epoch millis).
     *
     * @return number of rows removed
     */
    int deleteExpired(long olderThan);
}
