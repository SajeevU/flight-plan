package com.flightplan.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeoPointTest {

    @Test
    void parsesTheCaasFormat() {
        assertThat(GeoPoint.parse("WSSL (1.42,103.87)")).isEqualTo(new GeoPoint("WSSL", 1.42, 103.87));
    }

    @Test
    void handlesNegativeCoordinatesAndSpaces() {
        assertThat(GeoPoint.parse(" LAMOB ( -4.2 , -106.4 ) ")).isEqualTo(new GeoPoint("LAMOB", -4.2, -106.4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOCOORDS", "BAD (91,0)", "BAD (0,181)", "BAD (a,b)", ""})
    void rejectsMalformedEntries(String entry) {
        assertThat(GeoPoint.parse(entry)).isNull();
    }

    @Test
    void dropsInvalidEntriesFromAList() {
        assertThat(GeoPoint.parseAll(List.of("A (1,2)", "junk", "B (3,4)"))).hasSize(2);
        assertThat(GeoPoint.parseAll(null)).isEmpty();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout60Nm() {
        assertThat(new GeoPoint("A", 0, 0).distanceNm(new GeoPoint("B", 1, 0))).isCloseTo(60, within(0.5));
    }
}
