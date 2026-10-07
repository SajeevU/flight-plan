package com.flightplan.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightplan.geo.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class AirwayTest {

    @Test
    void parsesTheCaasSearchFormat() {
        assertThat(Airway.parse("A464: [CMA,TOPAS, BEKOD]")).isEqualTo(new Airway("A464", List.of("CMA", "TOPAS", "BEKOD")));
        assertThat(Airway.parse("A464")).isNull();
        assertThat(Airway.parse("X: []").fixes()).isEmpty();
    }

    @Test
    void returnsFixesBetweenTwoPointsInFlyingOrder() {
        Airway a = new Airway("A1", List.of("P", "Q", "R", "S"));
        assertThat(a.between("P", "S")).containsExactly("Q", "R");
        assertThat(a.between("S", "P")).containsExactly("R", "Q");
        assertThat(a.between("P", "Q")).isEmpty();
        assertThat(a.between("P", "NOPE")).isEmpty();
    }

    @Test
    void placesAmbiguousFixesNextToTheirNeighbours() {
        // "DUP" exists twice; the airway runs near the equator, so the one at 0,2 must be chosen.
        GeoIndex geo = GeoIndex.build(
                List.of(new GeoPoint("DUP", 50, 50), new GeoPoint("DUP", 0, 2), new GeoPoint("ONE", 0, 1), new GeoPoint("TWO", 0, 3)),
                List.of(), List.of());
        List<RoutePoint> path = new Airway("A1", List.of("DUP", "ONE", "TWO")).path(geo);
        assertThat(path).extracting(RoutePoint::name).containsExactly("DUP", "ONE", "TWO");
        assertThat(path.getFirst().lon()).isEqualTo(2.0);
        assertThat(path).allMatch(p -> "A1".equals(p.airway()));
    }
}
