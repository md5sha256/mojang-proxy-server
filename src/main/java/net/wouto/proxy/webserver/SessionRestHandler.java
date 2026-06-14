package net.wouto.proxy.webserver;

import net.wouto.proxy.MojangProxyServer;
import net.wouto.proxy.cache.GameProfileCache;
import net.wouto.proxy.metrics.MetricsService;
import net.wouto.proxy.response.result.HasJoinedMinecraftServerResponseImpl;
import net.wouto.proxy.response.result.MinecraftProfilePropertiesResponseImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionRestHandler {

	private static final Logger log = LoggerFactory.getLogger(SessionRestHandler.class);

	private final GameProfileCache cache;
	private final MetricsService metrics;

	public SessionRestHandler(MetricsService metrics) {
		this.cache = MojangProxyServer.get().getGameProfileCache();
		this.metrics = metrics;
	}

	@RequestMapping(value = "/session/minecraft/hasJoined", method = RequestMethod.GET)
	@ResponseBody
	public HasJoinedMinecraftServerResponseImpl hasJoined(
			@RequestParam(value = "serverId") String serverId,
			@RequestParam(value = "username") String username,
			@RequestParam(value = "proxyKey", required = false) String key) throws Exception {
		MojangProxyServer.authorize(key);
		this.metrics.increment(MetricsService.ENDPOINT_HAS_JOINED);
		if (MojangProxyServer.LOG_KNOWN_REQUESTS) {
			log.debug("forwarding hasJoined(username:\"{}\", serverId:\"{}\")", username, serverId);
		}
		return this.cache.hasJoined(username, serverId);
	}

	@RequestMapping(value = "/session/minecraft/profile/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	public MinecraftProfilePropertiesResponseImpl fillGameProfile(
			@PathVariable(value = "uuid") String uuid,
			@RequestParam(value = "unsigned", required = false, defaultValue = "true") boolean unsigned,
			@RequestParam(value = "proxyKey", required = false) String key) throws Exception {
		MojangProxyServer.authorize(key);
		this.metrics.increment(MetricsService.ENDPOINT_FILL_GAME_PROFILE);
		if (MojangProxyServer.LOG_KNOWN_REQUESTS) {
			log.debug("forwarding fillGameProfile(uuid:\"{}\", unsigned:{})", uuid, unsigned);
		}
		return this.cache.fillGameProfile(uuid, unsigned);
	}

}
