package com.flightplan.flights;

import com.flightplan.caas.CaasClient;
import com.flightplan.caas.CaasClient.GeoDataset;
import com.flightplan.caas.FlightObject;
import com.flightplan.geo.GeoPoint;
import com.flightplan.route.GeoIndex;
import com.flightplan.route.RouteGraph;
import com.flightplan.route.RoutePoint;
import com.flightplan.route.RouteResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlightsService {

    public record FlightSummary(
            String id,
            String callsign,
            String departure,
            String destination,
            String aircraftType,
            String dateOfFlight,
            String estimatedOffBlockTime) {}

    public record FlightRoute(FlightSummary flight, List<RoutePoint> points, List<String> unresolved) {}

    private final CaasClient caas;

    public FlightsService(CaasClient caas) {
        this.caas = caas;
    }

    /** Flights sorted by callsign; {@code callsign} is a case-insensitive substring filter. */
    public List<FlightSummary> list(String callsign) {
        String term = callsign == null ? "" : callsign.trim().toUpperCase(Locale.ROOT);
        return caas.listFlights().stream()
                .map(FlightsService::toSummary)
                .filter(f -> term.isEmpty() || f.callsign().toUpperCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(FlightSummary::callsign))
                .toList();
    }

    public FlightRoute route(String id) {
        FlightObject flight = find(id);
        var resolved = RouteResolver.resolve(flight, geoIndex());
        return new FlightRoute(toSummary(flight), resolved.points(), resolved.unresolved());
    }

    public FlightRoute alternateRoute(String id) {
        FlightObject flight = find(id);
        GeoIndex geo = geoIndex();
        var filed = RouteResolver.resolve(flight, geo);
        var others = caas.listFlights().stream().map(f -> RouteResolver.resolve(f, geo).points()).toList();
        List<RoutePoint> alternate = RouteGraph.build(geo, others).alternateTo(filed.points())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alternate route found in the known airway network"));
        return new FlightRoute(toSummary(flight), alternate, List.of());
    }

    private FlightObject find(String id) {
        return caas.listFlights().stream()
                .filter(f -> flightId(f).equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight " + id + " not found"));
    }

    private GeoIndex geoIndex() {
        List<GeoPoint> airports;
        try {
            airports = GeoPoint.parseAll(caas.listGeo(GeoDataset.AIRPORTS));
        } catch (RuntimeException e) {
            airports = List.of(); // optional: some datasets fold airports into fixes
        }
        return GeoIndex.build(
                GeoPoint.parseAll(caas.listGeo(GeoDataset.FIXES)),
                airports,
                GeoPoint.parseAll(caas.listGeo(GeoDataset.AIRWAYS)));
    }

    static String flightId(FlightObject f) {
        if (f.gufi() != null) return f.gufi();
        if (f._id() != null) return f._id();
        String date = f.departure() == null || f.departure().dateOfFlight() == null ? "" : f.departure().dateOfFlight();
        return f.aircraftIdentification() + "-" + date;
    }

    static FlightSummary toSummary(FlightObject f) {
        var dep = f.departure();
        return new FlightSummary(
                flightId(f),
                f.aircraftIdentification() == null ? "UNKNOWN" : f.aircraftIdentification(),
                f.departureCode(),
                f.destinationCode(),
                f.aircraftTypeCode(),
                dep == null ? null : dep.dateOfFlight(),
                dep == null ? null : dep.estimatedOffBLockTime());
    }
}
