package com.flightplan.airways;

import com.flightplan.caas.CaasClient;
import com.flightplan.caas.CaasClient.GeoDataset;
import com.flightplan.geo.GeoPoint;
import com.flightplan.route.Airway;
import com.flightplan.route.GeoIndex;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Aeronautical reference data in usable form: the point index built from the fixes, navaids
 * and airports lists, and airways looked up by name.
 */
@Component
public class AeroData {

    /** Parallel airway lookups against CAAS, kept modest to be polite to the API. */
    private static final int MAX_PARALLEL_LOOKUPS = 8;

    private static final Logger log = LoggerFactory.getLogger(AeroData.class);

    private final CaasClient caas;
    private volatile Built built;

    private record Built(List<String> fixes, List<String> navaids, List<String> airports, GeoIndex index) {}

    public AeroData(CaasClient caas) {
        this.caas = caas;
    }

    /** Downloads and indexes the large datasets in the background, so the first user doesn't wait for them. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.ofVirtual().name("aero-warmup").start(() -> {
            try {
                caas.listFlights();
                caas.listAirwayNames();
                geoIndex();
            } catch (RuntimeException e) {
                log.warn("Warm-up failed: {}", e.getMessage());
            }
        });
    }

    /** Index of all named points, rebuilt only when CAAS returns new lists. */
    public GeoIndex geoIndex() {
        List<String> fixes = caas.listGeo(GeoDataset.FIXES);
        List<String> navaids = caas.listGeo(GeoDataset.NAVAIDS);
        List<String> airports = caas.listGeo(GeoDataset.AIRPORTS);
        Built b = built;
        if (b != null && b.fixes() == fixes && b.navaids() == navaids && b.airports() == airports) return b.index();
        synchronized (this) {
            b = built;
            if (b != null && b.fixes() == fixes && b.navaids() == navaids && b.airports() == airports) return b.index();
            GeoIndex index = GeoIndex.build(GeoPoint.parseAll(fixes), GeoPoint.parseAll(navaids), GeoPoint.parseAll(airports));
            built = new Built(fixes, navaids, airports, index);
            return index;
        }
    }

    public List<String> airwayNames() {
        return caas.listAirwayNames();
    }

    /** The airway with exactly this name (the search endpoint also returns partial matches), or null. */
    public Airway airway(String name) {
        return caas.searchAirways(name).stream()
                .map(Airway::parse)
                .filter(a -> a != null && a.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** Looks several airways up in parallel so their responses are cached before they are needed. */
    public void prefetch(Collection<String> names) {
        Semaphore permits = new Semaphore(MAX_PARALLEL_LOOKUPS);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String name : names) {
                pool.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        return airway(name);
                    } finally {
                        permits.release();
                    }
                });
            }
        }
    }
}
