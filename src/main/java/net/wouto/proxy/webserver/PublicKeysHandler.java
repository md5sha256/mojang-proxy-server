package net.wouto.proxy.webserver;

import net.wouto.proxy.MojangProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Relays Mojang's signing public keys so a server pointed at this proxy via
 * {@code minecraft.api.services.host} can verify signed chat ("secure profiles").
 * <p>
 * This is a pure passthrough of {@code https://api.minecraftservices.com/publickeys},
 * cached in memory — the proxy performs no authentication of its own.
 */
@RestController
public class PublicKeysHandler {

	private static final Logger log = LoggerFactory.getLogger(PublicKeysHandler.class);

	private static final URI MOJANG_PUBLIC_KEYS = URI.create("https://api.minecraftservices.com/publickeys");
	private static final long CACHE_TTL_MILLIS = 60 * 60 * 1000L;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private volatile String cachedBody;
	private volatile long fetchedAt;

	@GetMapping(value = "/publickeys", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> publicKeys() throws Exception {
		String body = this.cachedBody;
		long now = System.currentTimeMillis();
		if (body == null || now - this.fetchedAt > CACHE_TTL_MILLIS) {
			try {
				body = fetch();
				this.cachedBody = body;
				this.fetchedAt = now;
			} catch (Exception e) {
				if (body == null) {
					throw e;
				}
				log.warn("publickeys upstream fetch failed; serving stale cache", e);
			}
		}
		if (MojangProxyServer.LOG_KNOWN_REQUESTS) {
			log.debug("relaying publicKeys()");
		}
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private String fetch() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(MOJANG_PUBLIC_KEYS)
				.GET()
				.header("Accept", "application/json")
				.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return response.body();
	}

}
