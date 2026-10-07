package com.flightplan.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightplan.route.RoutePoint.Kind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteGraphTest {

    private static RoutePoint p(String name, double lat, double lon) {
        return new RoutePoint(name, lat, lon, Kind.WAYPOINT, null);
    }

    private static RoutePoint p(String name, double lat, double lon, String airway) {
        return new RoutePoint(name, lat, lon, Kind.WAYPOINT, airway);
    }

    // Two parallel airways from DEP to ARR: NORTH via N (shorter) and SOUTH via S.
    private final RouteGraph graph = RouteGraph.build(List.of(
            List.of(p("DEP", 0, 0, "NORTH"), p("N", 1, 1, "NORTH"), p("ARR", 0, 2)),
            List.of(p("DEP", 0, 0, "SOUTH"), p("S", -1.5, 1, "SOUTH"), p("ARR", 0, 2))));

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
