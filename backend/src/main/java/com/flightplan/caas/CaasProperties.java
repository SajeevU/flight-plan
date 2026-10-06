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
        @DefaultValue("http://api.swimapisg.info:9080") String baseUrl,
        @DefaultValue("/flightmanager/displayAll") String flightsPath,
        @DefaultValue("/geopoints/list") String geoPath,
        String apiKey,
        /* Serve the bundled fixtures instead of calling CAAS. Also used when no key is set. */
        @DefaultValue("false") boolean mock,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("60s") Duration flightsTtl,
        @DefaultValue("6h") Duration geoTtl,
        /* How long to serve fixtures after CAAS fails before trying it again. */
        @DefaultValue("5m") Duration fallbackTtl) {

    public boolean useFixturesOnly() {
        return mock || apiKey == null || apiKey.isBlank();
    }
}
