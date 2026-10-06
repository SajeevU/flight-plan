package com.flightplan.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightplan.geo.GeoPoint;
import com.flightplan.route.RoutePoint.Kind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteGraphTest {

    // Two parallel airways from DEP to ARR: NORTH via N (shorter) and SOUTH via S.
    private static final GeoIndex GEO = GeoIndex.build(
            List.of(new GeoPoint("N", 1, 1), new GeoPoint("S", -1.5, 1)),
            List.of(new GeoPoint("DEP", 0, 0), new GeoPoint("ARR", 0, 2)),
            List.of(new GeoPoint("NORTH", 0, 0), new GeoPoint("NORTH", 1, 1), new GeoPoint("NORTH", 0, 2),
                    new GeoPoint("SOUTH", 0, 0), new GeoPoint("SOUTH", -1.5, 1), new GeoPoint("SOUTH", 0, 2)));

    private final RouteGraph graph = RouteGraph.build(GEO, List.of());

    private static RoutePoint p(String name, double lat, double lon) {
        return new RoutePoint(name, lat, lon, Kind.WAYPOINT, null);
    }

    @Test
    void findsTheShortestPathWhenNothingIsAvoided() {
        var path = graph.shortestPath(p("DEP", 0, 0), p("ARR", 0, 2), Set.of()).orElseThrow();
        assertThat(path).extracting(RoutePoint::name).containsExactly("DEP", "N", "ARR");
        assertThat(path.getFirst().airway()).isEqualTo("NORTH");
    }

    @Test
    void routesAroundTheFiledWaypoints() {
        var filed = List.of(p("DEP", 0, 0), p("N", 1, 1), p("ARR", 0, 2));
        assertThat(graph.alternateTo(filed).orElseThrow()).extracting(RoutePoint::name).containsExactly("DEP", "S", "ARR");
    }

    @Test
    void isEmptyWhenNoAlternativeExists() {
        var filed = List.of(p("DEP", 0, 0), p("N", 1, 1), p("S", -1.5, 1), p("ARR", 0, 2));
        assertThat(graph.alternateTo(filed)).isEmpty();
    }

    @Test
    void isEmptyForUnknownEndpointsOrTooShortRoutes() {
        assertThat(graph.alternateTo(List.of(p("X", 50, 50), p("Y", 51, 51)))).isEmpty();
        assertThat(graph.alternateTo(List.of(p("DEP", 0, 0)))).isEmpty();
    }
}
