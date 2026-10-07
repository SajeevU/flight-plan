package com.flightplan.caas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
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
 * since the aeronautical datasets are large (the fixes list alone is ~250k entries) and change rarely.
 *
 * <p>If CAAS is unreachable (or no key is configured) it serves offline data instead, so the app
 * stays usable and {@link #dataSource()} says which data is being shown. The offline data is a
 * snapshot of the real CAAS data in {@code resources/snapshot} when the build took one (CI does,
 * see scripts/snapshot_caas.sh), otherwise the illustrative {@code resources/fixtures}.
 * After a failure it retries CAAS once {@code caas.fallback-ttl} expires.
 */
@Component
public class CaasClient {

    /** The "NAME (lat,lon)" datasets of the aeronautical data service. */
    public enum GeoDataset { FIXES, NAVAIDS, AIRPORTS }

    public enum DataSource { CAAS, FIXTURES, FIXTURES_CAAS_UNREACHABLE }

    private static final Logger log = LoggerFactory.getLogger(CaasClient.class);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private record Entry(Instant expires, Object value) {}

    /** Result of one load: the data plus whether it came from the fixture fallback. */
    private record Loaded<T>(T value, boolean fallback) {}

    private final CaasProperties props;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    /** Parsed offline files, read once. */
    private final Map<String, Object> offline = new ConcurrentHashMap<>();
    /** "snapshot" or "fixtures": the classpath folder offline data is read from. */
    private final String offlineDir;
    private final String snapshotTakenAt;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    /** Cache keys currently holding fixture data because CAAS failed for them. */
    private final java.util.Set<String> fallbackKeys = ConcurrentHashMap.newKeySet();
    /** After a failure, skip CAAS until this time so every dataset doesn't wait out its own timeout. */
    private volatile Instant caasDownUntil = Instant.MIN;
    private volatile String lastError;

    public CaasClient(CaasProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        var jdk = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.readTimeout());
        this.http = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
        this.offlineDir = !props.useFixturesOnly() && new ClassPathResource("snapshot/flights.json").exists() ? "snapshot" : "fixtures";
        this.snapshotTakenAt = offlineDir.equals("snapshot") ? readSnapshotTime() : null;
        if (props.useFixturesOnly()) log.warn("No CAAS API key set (or caas.mock=true): serving bundled fixture data");
        else log.info("Offline data if CAAS is unreachable: {}", offlineDir);
    }

    /** Fixtures by configuration, fixtures because some CAAS call failed, or live CAAS data. */
    public DataSource dataSource() {
        if (props.useFixturesOnly()) return DataSource.FIXTURES;
        return fallbackKeys.isEmpty() ? DataSource.CAAS : DataSource.FIXTURES_CAAS_UNREACHABLE;
    }

    /** When the bundled CAAS snapshot was taken, or null when the offline data is the sample fixtures. */
    public String snapshotTakenAt() {
        return snapshotTakenAt;
    }

    /** Why the last CAAS call failed (for /api/health), while any fixture fallback is in use. Never contains the API key. */
    public String lastError() {
        return fallbackKeys.isEmpty() ? null : lastError;
    }

    public List<FlightObject> listFlights() {
        return cached("flights", props.flightsTtl(), () -> fetch(props.flightsPath(),
                new ParameterizedTypeReference<List<FlightObject>>() {},
                () -> fixture("flights.json", new TypeReference<List<FlightObject>>() {})));
    }

    /** All entries of a "NAME (lat,lon)" dataset. */
    public List<String> listGeo(GeoDataset type) {
        String name = type.name().toLowerCase();
        return cached("geo:" + name, props.geoTtl(), () -> fetch(props.geoPath() + "/list/" + name,
                new ParameterizedTypeReference<List<String>>() {}, () -> fixture(name + ".json", STRINGS)));
    }

    /** Names of all airways. The list endpoint returns names only, without their fixes. */
    public List<String> listAirwayNames() {
        return cached("geo:airways", props.geoTtl(), () -> fetch(props.geoPath() + "/list/airways",
                new ParameterizedTypeReference<List<String>>() {}, () -> fixture("airways.json", STRINGS)));
    }

    /**
     * Airways whose name contains {@code term}, as "NAME: [FIX1,FIX2,...]" strings with the fixes
     * in published order. This is the only endpoint that returns an airway's fixes.
     */
    public List<String> searchAirways(String term) {
        return cached("airway:" + term, props.geoTtl(), () -> fetch(props.geoPath() + "/search/airways/" + term,
                new ParameterizedTypeReference<List<String>>() {}, () -> fixtureAirwaySearch(term)));
    }

    private <T> Loaded<T> fetch(String path, ParameterizedTypeReference<T> type, Supplier<T> fixture) {
        if (props.useFixturesOnly()) return new Loaded<>(fixture.get(), false);
        if (Instant.now().isBefore(caasDownUntil)) return new Loaded<>(fixture.get(), true);
        try {
            T body = http.get().uri(path)
                    .header("apikey", props.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(type);
            return new Loaded<>(body, false);
        } catch (RuntimeException e) {
            log.error("CAAS {} failed ({}); serving fixture data", path, e.getMessage());
            lastError = Instant.now() + " " + path + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
            caasDownUntil = Instant.now().plus(props.fallbackTtl());
            return new Loaded<>(fixture.get(), true);
        }
    }

    private List<String> fixtureAirwaySearch(String term) {
        List<String> all = fixture("airway-search.json", STRINGS);
        // Entries look like "A464: [FIX1,...]"; match on the name part, as the live search does.
        return all.stream().filter(entry -> entry.substring(0, entry.indexOf(':')).contains(term)).toList();
    }

    @SuppressWarnings("unchecked")
    private <T> T fixture(String name, TypeReference<T> type) {
        return (T) offline.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource(offlineDir + "/" + n).getInputStream()) {
                return mapper.readValue(in, type);
            } catch (IOException e) {
                throw new IllegalStateException("Missing offline data " + offlineDir + "/" + n, e);
            }
        });
    }

    private String readSnapshotTime() {
        try (InputStream in = new ClassPathResource("snapshot/meta.json").getInputStream()) {
            return mapper.readTree(in).path("takenAt").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Duration ttl, Supplier<Loaded<T>> load) {
        Entry hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(Instant.now())) return (T) hit.value();
        // One loader per key, so concurrent requests don't all hit CAAS at once.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            hit = cache.get(key);
            if (hit != null && hit.expires().isAfter(Instant.now())) return (T) hit.value();
            Loaded<T> loaded = load.get();
            // Fallback data is kept briefly so CAAS is retried soon.
            var expires = Instant.now().plus(loaded.fallback() ? props.fallbackTtl() : ttl);
            if (loaded.fallback()) fallbackKeys.add(key);
            else fallbackKeys.remove(key);
            cache.put(key, new Entry(expires, loaded.value()));
            return loaded.value();
        }
    }
}
