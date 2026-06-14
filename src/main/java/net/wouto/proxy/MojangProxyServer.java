package net.wouto.proxy;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import net.wouto.proxy.cache.GameProfileCache;
import net.wouto.proxy.exception.InvalidProxyKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

@SpringBootApplication
public class MojangProxyServer {

	private static final Logger log = LoggerFactory.getLogger(MojangProxyServer.class);

	public static boolean LOG_UNKNOWN_REQUESTS = false;
	public static boolean LOG_KNOWN_REQUESTS = true;
	public static String GAME_PROFILE_CACHE_CLASS = null;
	public static String AUTH_KEY = null;

	private static MojangProxyServer instance;
	private JsonMapper objectMapper;
	private JsonMapper objectMapperPretty;
	private GameProfileCache gameProfileCache;

	private static Config config;

	public void start(String[] args) {
 		this.objectMapper = JsonMapper.builder().build();
		this.objectMapperPretty = JsonMapper.builder()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.build();
		if (GAME_PROFILE_CACHE_CLASS == null) {
			this.gameProfileCache = new GameProfileCache(MojangProxyServer.config);
			log.info("Using default in-memory GameProfileCache");
		} else {
			try {
				Class<GameProfileCache> c = (Class<GameProfileCache>) Class.forName(GAME_PROFILE_CACHE_CLASS);
				Constructor<GameProfileCache> cons = c.getDeclaredConstructor(Config.class);
				this.gameProfileCache = cons.newInstance(MojangProxyServer.config);
			} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
				log.error("Failed to load GameProfileCache '{}'", GAME_PROFILE_CACHE_CLASS, e);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Config c = Config.getConfig(new File("proxy.properties"));
		config = c;

		LOG_UNKNOWN_REQUESTS = Boolean.parseBoolean(c.getProperty("logAllUnknownRequests", "false"));
		LOG_KNOWN_REQUESTS = Boolean.parseBoolean(c.getProperty("logKnownRequests", "true"));
		GAME_PROFILE_CACHE_CLASS = c.getProperty("gameProfileCacheClass", null);
		AUTH_KEY = c.getProperty("authKey", null);

		if (GAME_PROFILE_CACHE_CLASS != null && GAME_PROFILE_CACHE_CLASS.isEmpty()) {
			GAME_PROFILE_CACHE_CLASS = null;
		}

		String host = c.getProperty("hostname", "0.0.0.0");
		int port = Integer.parseInt(c.getProperty("port", "8000"));

		HashMap<String, Object> props = new HashMap<>();
		props.put("server.address", host);
		props.put("server.port", port);

		instance = new MojangProxyServer();
		instance.start(args);
		new SpringApplicationBuilder()
				.sources(MojangProxyServer.class)
				.properties(props)
				.run(args);
	}

	public static void authorize(String key) throws InvalidProxyKeyException {
		if (AUTH_KEY == null) {
			return;
		}
		if (key.equals(AUTH_KEY)) {
			return;
		}
		throw new InvalidProxyKeyException();
	}

	public GameProfileCache getGameProfileCache() {
		return gameProfileCache;
	}

	public static MojangProxyServer get() {
		return instance;
	}

	public JsonMapper getMapper(boolean pretty) {
		return (pretty ? this.objectMapperPretty : this.objectMapper);
	}

}
