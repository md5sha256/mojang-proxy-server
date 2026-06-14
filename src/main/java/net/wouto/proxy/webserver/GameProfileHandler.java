package net.wouto.proxy.webserver;

import com.mojang.authlib.GameProfile;
import net.wouto.proxy.MojangProxyServer;
import net.wouto.proxy.cache.GameProfileCache;
import net.wouto.proxy.metrics.MetricsService;
import net.wouto.proxy.response.result.BasicGameProfile;
import net.wouto.proxy.response.result.ProfileSearchResultsResponseImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class GameProfileHandler {

	private static final Logger log = LoggerFactory.getLogger(GameProfileHandler.class);

	private final GameProfileCache cache;
	private final MetricsService metrics;

	public GameProfileHandler(MetricsService metrics) {
		this.cache = MojangProxyServer.get().getGameProfileCache();
		this.metrics = metrics;
	}

	@RequestMapping(value = "/profiles/minecraft", method = RequestMethod.POST)
	@ResponseBody
	public BasicGameProfile[] findProfilesByNames(@RequestBody List<String> names, @RequestParam(value = "proxyKey", required = false) String key) throws Exception {
		MojangProxyServer.authorize(key);
		this.metrics.increment(MetricsService.ENDPOINT_FIND_PROFILES_BY_NAMES);
		if (MojangProxyServer.LOG_KNOWN_REQUESTS) {
			log.debug("forwarding findProfilesByNames(names:[\"{}\"])", String.join("\", \"", names));
		}
		ProfileSearchResultsResponseImpl response = this.cache.findProfilesByNames(names);
		if (response.getProfiles() == null) {
			return new BasicGameProfile[0];
		}
		List<BasicGameProfile> profiles = new ArrayList<>();
		for (GameProfile gameProfile : response.getProfiles()) {
			if (gameProfile != null) {
				profiles.add(new BasicGameProfile(gameProfile.getId().toString().replace("-", ""), gameProfile.getName()));
			}
		}
		return profiles.toArray(new BasicGameProfile[profiles.size()]);
	}

}
