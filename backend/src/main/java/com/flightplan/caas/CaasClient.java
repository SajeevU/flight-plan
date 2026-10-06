package com.flightplan.caas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the CAAS SWIM APIs. Keeps the API key server-side and caches responses,
 * since the airway and fix datasets are large and change rarely.
 *
 * <p>If CAAS is unreachable (or no key is configured) it serves the bundled fixtures in
 * {@code resources/fixtures}, so the app stays usable and {@link #dataSource()} says which
 * data is being shown. After a failure it retries CAAS once {@code caas.fallback-ttl} expires.
 */
@Component
public class CaasClient {

    public enum GeoDataset { AIRWAYS, FIXES, AIRPORTS }

    public enum DataSource { CAAS, FIXTURES, FIXTURES_CAAS_UNREACHABLE }

    private static final Logger log = LoggerFactory.getLogger(CaasClient.class);

    private record Entry(Instant expires, Object value) {}

    private final CaasProperties props;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private volatile DataSource dataSource;
    /** After a failure, skip CAAS until this time so every dataset doesn't wait out its own timeout. */
    private volatile Instant caasDownUntil = Instant.MIN;

    public CaasClient(CaasProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        var jdk = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.timeout());
        this.http = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
        this.dataSource = props.useFixturesOnly() ? DataSource.FIXTURES : DataSource.CAAS;
        if (props.useFixturesOnly()) log.warn("No CAAS API key set (or caas.mock=true): serving bundled fixture data");
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public List<FlightObject> listFlights() {
        return cached("flights", props.flightsTtl(), () -> fetch(props.flightsPath(), "flights.json",
                new ParameterizedTypeReference<List<FlightObject>>() {}, new TypeReference<List<FlightObject>>() {}));
    }

    public List<String> listGeo(GeoDataset type) {
        String name = type.name().toLowerCase();
        return cached("geo:" + name, props.geoTtl(), () -> fetch(props.geoPath() + "/" + name, name + ".json",
                new ParameterizedTypeReference<List<String>>() {}, new TypeReference<List<String>>() {}));
    }

    /** Result of one load: the data plus whether it came from the fixture fallback. */
    private record Loaded<T>(T value, boolean fallback) {}

    private <T> Loaded<T> fetch(String path, String fixture, ParameterizedTypeReference<T> type, TypeReference<T> fixtureType) {
        if (props.useFixturesOnly()) return new Loaded<>(fixture(fixture, fixtureType), false);
        if (Instant.now().isBefore(caasDownUntil)) return new Loaded<>(fixture(fixture, fixtureType), true);
        try {
            T body = http.get().uri(path)
                    .header("apikey", props.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(type);
            dataSource = DataSource.CAAS;
            return new Loaded<>(body, false);
        } catch (RuntimeException e) {
            log.error("CAAS {} failed ({}); serving fixture {}", path, e.getMessage(), fixture);
            dataSource = DataSource.FIXTURES_CAAS_UNREACHABLE;
            caasDownUntil = Instant.now().plus(props.fallbackTtl());
            return new Loaded<>(fixture(fixture, fixtureType), true);
        }
    }

    private <T> T fixture(String name, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource("fixtures/" + name).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Missing fixture " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.time.Duration ttl, Supplier<Loaded<T>> load) {
        Entry hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(Instant.now())) return (T) hit.value();
        // One loader per key, so concurrent requests don't all hit CAAS at once.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            hit = cache.get(key);
            if (hit != null && hit.expires().isAfter(Instant.now())) return (T) hit.value();
            Loaded<T> loaded = load.get();
            // Fallback data is kept briefly so CAAS is retried soon.
            var expires = Instant.now().plus(loaded.fallback() ? props.fallbackTtl() : ttl);
            cache.put(key, new Entry(expires, loaded.value()));
            return loaded.value();
        }
    }
}
