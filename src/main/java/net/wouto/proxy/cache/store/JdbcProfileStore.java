package net.wouto.proxy.cache.store;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.wouto.proxy.response.result.BasicProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed {@link ProfileStore}. The only engine-specific detail here is the
 * {@code INSERT OR REPLACE} upsert; everything else is portable JDBC, so a
 * different backend is a matter of swapping this one class.
 */
@Repository
public class JdbcProfileStore implements ProfileStore {

    // INSERT OR REPLACE atomically deletes any row violating the uuid primary key
    // OR the unique name_lower index before inserting. So re-caching the same uuid
    // updates it, and a name changing hands evicts its previous holder in one step.
    private static final String UPSERT =
            "INSERT OR REPLACE INTO game_profile (uuid, name, name_lower, properties, cached_at) " +
            "VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public JdbcProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GameProfile> findByUuid(UUID uuid, long minCachedAt) {
        List<GameProfile> rows = jdbc.query(
                "SELECT uuid, name, properties FROM game_profile WHERE uuid = ? AND cached_at >= ?",
                this::mapProfile, uuid.toString(), minCachedAt);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<GameProfile> findByName(String name, long minCachedAt) {
        List<GameProfile> rows = jdbc.query(
                "SELECT uuid, name, properties FROM game_profile WHERE name_lower = ? AND cached_at >= ?",
                this::mapProfile, name.toLowerCase(Locale.ROOT), minCachedAt);
        return rows.stream().findFirst();
    }

    @Override
    public void save(GameProfile profile, long cachedAt) {
        jdbc.update(UPSERT,
                profile.getId().toString(),
                profile.getName(),
                profile.getName().toLowerCase(Locale.ROOT),
                serializeProperties(profile),
                cachedAt);
    }

    @Override
    public int deleteExpired(long olderThan) {
        return jdbc.update("DELETE FROM game_profile WHERE cached_at < ?", olderThan);
    }

    private GameProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        GameProfile profile = new GameProfile(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
        BasicProperty[] props = mapper.readValue(rs.getString("properties"), BasicProperty[].class);
        for (BasicProperty bp : props) {
            profile.getProperties().put(bp.getName(), new Property(bp.getName(), bp.getValue(), bp.getSignature()));
        }
        return profile;
    }

    private String serializeProperties(GameProfile profile) {
        List<BasicProperty> props = profile.getProperties().values().stream()
                .map(p -> p.hasSignature()
                        ? new BasicProperty(p.getName(), p.getValue(), p.getSignature())
                        : new BasicProperty(p.getName(), p.getValue()))
                .toList();
        return mapper.writeValueAsString(props);
    }
}
