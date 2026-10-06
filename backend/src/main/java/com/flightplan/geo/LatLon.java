package com.flightplan.geo;

/** Anything with a position. */
public interface LatLon {

    double EARTH_RADIUS_NM = 3440.065;

    double lat();

    double lon();

    /** Great-circle (haversine) distance in nautical miles. */
    default double distanceNm(LatLon other) {
        double dLat = Math.toRadians(other.lat() - lat());
        double dLon = Math.toRadians(other.lon() - lon());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat())) * Math.cos(Math.toRadians(other.lat())) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_NM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Coordinates rounded to ~100 m, used to recognise the same point coming from different datasets. */
    default String key() {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f", lat(), lon());
    }
}
