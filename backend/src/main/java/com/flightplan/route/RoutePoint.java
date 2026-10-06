package com.flightplan.route;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.flightplan.geo.LatLon;

/**
 * One point on a drawn route.
 *
 * @param airway airway flown from this point to the next one, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutePoint(String name, double lat, double lon, Kind kind, String airway) implements LatLon {

    public enum Kind {
        AIRPORT, WAYPOINT, AIRWAY;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public RoutePoint withAirway(String airway) {
        return new RoutePoint(name, lat, lon, kind, airway);
    }

    public boolean samePlace(LatLon other) {
        return other != null && lat == other.lat() && lon == other.lon();
    }
}
