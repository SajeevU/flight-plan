package com.flightplan.caas;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the CAAS SWIM APIs, bound from {@code caas.*} in application.yml
 * (each overridable with an environment variable, e.g. CAAS_API_KEY).
 */
@ConfigurationProperties(prefix = "caas")
public record CaasProperties(
        @DefaultValue("https://api.swimapisg.info") String baseUrl,
        @DefaultValue("/flight-manager/displayAll") String flightsPath,
        @DefaultValue("/geopoints") String geoPath,
        String apiKey,
        /* Serve the bundled fixtures instead of calling CAAS. Also used when no key is set. */
        @DefaultValue("false") boolean mock,
        @DefaultValue("5s") Duration connectTimeout,
        /* Generous: the fixes list is ~5.6 MB. */
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("60s") Duration flightsTtl,
        @DefaultValue("6h") Duration geoTtl,
        /* While CAAS is failing, serve fixtures and retry it at most this often. */
        @DefaultValue("5m") Duration fallbackTtl) {

    public boolean useFixturesOnly() {
        return mock || apiKey == null || apiKey.isBlank();
    }
}
