package com.flightplan.flights;

import com.flightplan.airways.AeroData;
import com.flightplan.caas.CaasClient;
import com.flightplan.caas.FlightObject;
import com.flightplan.route.Airway;
import com.flightplan.route.GeoIndex;
import com.flightplan.route.RouteGraph;
import com.flightplan.route.RoutePoint;
import com.flightplan.route.RouteResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final AeroData aero;

    public FlightsService(CaasClient caas, AeroData aero) {
        this.caas = caas;
        this.aero = aero;
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
        aero.prefetch(RouteResolver.airwaysOf(flight));
        var resolved = RouteResolver.resolve(flight, aero.geoIndex(), aero::airway);
        return new FlightRoute(toSummary(flight), resolved.points(), resolved.unresolved());
    }

    /**
     * A route between the same airports that avoids every intermediate point of the filed one,
     * searched over the airways that any current flight plan uses plus all filed routes.
     */
    public FlightRoute alternateRoute(String id) {
        FlightObject flight = find(id);
        GeoIndex geo = aero.geoIndex();
        List<FlightObject> all = caas.listFlights();
        Set<String> airwayNames = new LinkedHashSet<>();
        all.forEach(f -> airwayNames.addAll(RouteResolver.airwaysOf(f)));
        aero.prefetch(airwayNames);

        List<List<RoutePoint>> paths = new ArrayList<>();
        for (String name : airwayNames) {
            Airway airway = aero.airway(name);
            if (airway != null) paths.add(airway.path(geo));
        }
        all.forEach(f -> paths.add(RouteResolver.resolve(f, geo, aero::airway).points()));

        var filed = RouteResolver.resolve(flight, geo, aero::airway);
        List<RoutePoint> alternate = RouteGraph.build(paths).alternateTo(filed.points())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alternate route found in the known airway network"));
        return new FlightRoute(toSummary(flight), alternate, List.of());
    }

    private FlightObject find(String id) {
        return caas.listFlights().stream()
                .filter(f -> flightId(f).equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight " + id + " not found"));
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
