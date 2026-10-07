package com.flightplan.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightplan.caas.FlightObject;
import com.flightplan.geo.GeoPoint;
import com.flightplan.route.RoutePoint.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteResolverTest {

    private static final GeoIndex GEO = GeoIndex.build(
            List.of(new GeoPoint("AAA", 1, 1), new GeoPoint("BBB", 2, 2), new GeoPoint("CCC", 3, 3),
                    // Same name on the other side of the world: must never be picked.
                    new GeoPoint("BBB", -40, -120)),
            List.of(new GeoPoint("NAV", 1.5, 1.5)),
            List.of(new GeoPoint("DEP", 0, 0), new GeoPoint("ARR", 4, 4)));

    private static final Map<String, Airway> AIRWAYS = Map.of("X1", new Airway("X1", List.of("AAA", "BBB", "CCC")));

    private final ObjectMapper mapper = new ObjectMapper();

    /** Builds a flight from JSON, the way it arrives from CAAS. */
    private FlightObject flight(String routeElementsJson) throws Exception {
        return mapper.readValue("""
                {"aircraftIdentification":"TST1",
                 "departure":{"departureAerodrome":"DEP"},
                 "arrival":{"destinationAerodrome":"ARR"},
                 "filedRoute":{"routeElement":%s}}""".formatted(routeElementsJson), FlightObject.class);
    }

    private static RouteResolver.ResolvedRoute resolve(FlightObject f) {
        return RouteResolver.resolve(f, GEO, AIRWAYS::get);
    }

    @Test
    void ordersDepartureElementsBySeqNumThenDestination() throws Exception {
        var route = resolve(flight("""
                [{"seqNum":1,"position":{"designatedPoint":"CCC"}},{"seqNum":0,"position":{"designatedPoint":"NAV"}}]"""));
        assertThat(route.points()).extracting(RoutePoint::name).containsExactly("DEP", "NAV", "CCC", "ARR");
        assertThat(route.unresolved()).isEmpty();
    }

    @Test
    void insertsTheAirwayFixesBetweenTwoRoutePoints() throws Exception {
        var route = resolve(flight("""
                [{"seqNum":0,"position":{"designatedPoint":"AAA"},"airway":"X1"},{"seqNum":1,"position":{"designatedPoint":"CCC"}}]"""));
        assertThat(route.points()).extracting(RoutePoint::name).containsExactly("DEP", "AAA", "BBB", "CCC", "ARR");
        assertThat(route.points()).extracting(RoutePoint::kind)
                .containsExactly(Kind.AIRPORT, Kind.WAYPOINT, Kind.AIRWAY, Kind.WAYPOINT, Kind.AIRPORT);
        assertThat(route.points().get(2).lat()).as("the nearby BBB, not the one at -40,-120").isEqualTo(2.0);
        assertThat(route.points().get(1).airway()).isEqualTo("X1");
    }

    @Test
    void followsAnAirwayFlownInReverse() throws Exception {
        var route = resolve(flight("""
                [{"seqNum":0,"position":{"designatedPoint":"CCC"},"airway":"X1"},{"seqNum":1,"position":{"designatedPoint":"AAA"}}]"""));
        assertThat(route.points()).extracting(RoutePoint::name).containsExactly("DEP", "CCC", "BBB", "AAA", "ARR");
    }

    @Test
    void stripsSpeedAndLevelChangesFromTheAirwayField() {
        assertThat(RouteResolver.airwayName("M300/N0486F410")).isEqualTo("M300");
        assertThat(RouteResolver.airwayName("UL333")).isEqualTo("UL333");
        assertThat(RouteResolver.airwayName("DCT")).isNull();
        assertThat(RouteResolver.airwayName("42N160W/M085F380")).isNull();
    }

    @Test
    void prefersCoordinatesCarriedInTheFlightPlanEvenAsStrings() throws Exception {
        var route = resolve(flight("""
                [{"position":{"lat":"9","lon":"-150"},"airway":"38N180E"}]"""));
        assertThat(route.points().get(1)).extracting(RoutePoint::lat, RoutePoint::lon).containsExactly(9.0, -150.0);
        assertThat(route.points().get(1).airway()).isNull();
    }

    @Test
    void reportsDesignatorsItCannotPlace() throws Exception {
        FlightObject f = mapper.readValue("""
                {"departure":{"departureAerodrome":"DEP"},"arrival":{"destinationAerodrome":"ZZZZ"},
                 "filedRoute":{"routeElement":[{"position":{"designatedPoint":"NOPE"}}]}}""", FlightObject.class);
        assertThat(resolve(f).unresolved()).containsExactly("NOPE", "ZZZZ");
    }

    @Test
    void copesWithAFlightWithNoRouteAtAll() throws Exception {
        var route = resolve(mapper.readValue("{}", FlightObject.class));
        assertThat(route.points()).isEmpty();
        assertThat(route.unresolved()).isEmpty();
    }
}
