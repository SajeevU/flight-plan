package com.flightplan.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightplan.caas.FlightObject;
import com.flightplan.geo.GeoPoint;
import com.flightplan.route.RoutePoint.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteResolverTest {

    private static final GeoIndex GEO = GeoIndex.build(
            List.of(new GeoPoint("AAA", 1, 1), new GeoPoint("BBB", 2, 2), new GeoPoint("CCC", 3, 3)),
            List.of(new GeoPoint("DEP", 0, 0), new GeoPoint("ARR", 4, 4)),
            List.of(new GeoPoint("X1", 1, 1), new GeoPoint("X1", 2, 2), new GeoPoint("X1", 3, 3)));

    private final ObjectMapper mapper = new ObjectMapper();

    /** Builds a flight from JSON, the way it arrives from CAAS. */
    private FlightObject flight(String routeElementsJson) throws Exception {
        return mapper.readValue("""
                {"aircraftIdentification":"TST1",
                 "departure":{"departureAerodrome":"DEP"},
                 "arrival":{"destinationAerodrome":{"locationIndicator":"ARR"}},
                 "filedRoute":{"routeElement":%s}}""".formatted(routeElementsJson), FlightObject.class);
    }

    @Test
    void ordersDepartureElementsBySeqNumThenDestination() throws Exception {
        var route = RouteResolver.resolve(flight("""
                [{"seqNum":2,"position":{"designatedPoint":"CCC"}},{"seqNum":1,"position":{"designatedPoint":"AAA"}}]"""), GEO);
        assertThat(route.points()).extracting(RoutePoint::name).containsExactly("DEP", "AAA", "CCC", "ARR");
        assertThat(route.unresolved()).isEmpty();
    }

    @Test
    void expandsTheAirwayBetweenTwoRoutePoints() throws Exception {
        var route = RouteResolver.resolve(flight("""
                [{"seqNum":1,"position":{"designatedPoint":"AAA"},"airway":"X1"},{"seqNum":2,"position":{"designatedPoint":"CCC"}}]"""), GEO);
        assertThat(route.points()).extracting(RoutePoint::lat).containsExactly(0.0, 1.0, 2.0, 3.0, 4.0);
        assertThat(route.points()).extracting(RoutePoint::kind)
                .containsExactly(Kind.AIRPORT, Kind.WAYPOINT, Kind.AIRWAY, Kind.WAYPOINT, Kind.AIRPORT);
        assertThat(route.points().get(1).airway()).isEqualTo("X1");
    }

    @Test
    void prefersCoordinatesCarriedInTheFlightPlan() throws Exception {
        var route = RouteResolver.resolve(flight("""
                [{"position":{"designatedPoint":"AAA","lat":9,"lon":9}}]"""), GEO);
        assertThat(route.points().get(1)).extracting(RoutePoint::name, RoutePoint::lat).containsExactly("AAA", 9.0);
    }

    @Test
    void reportsDesignatorsItCannotPlace() throws Exception {
        FlightObject f = mapper.readValue("""
                {"departure":{"departureAerodrome":"DEP"},"arrival":{"destinationAerodrome":"ZZZZ"},
                 "filedRoute":{"routeElement":[{"position":{"designatedPoint":"NOPE"}}]}}""", FlightObject.class);
        assertThat(RouteResolver.resolve(f, GEO).unresolved()).containsExactly("NOPE", "ZZZZ");
    }

    @Test
    void copesWithAFlightWithNoRouteAtAll() throws Exception {
        var route = RouteResolver.resolve(mapper.readValue("{}", FlightObject.class), GEO);
        assertThat(route.points()).isEmpty();
        assertThat(route.unresolved()).isEmpty();
    }
}
