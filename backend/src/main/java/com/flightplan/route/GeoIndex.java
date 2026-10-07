package com.flightplan.route;

import com.flightplan.geo.GeoPoint;
import com.flightplan.geo.LatLon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup tables built from the aeronautical datasets.
 *
 * <p>Fix and navaid names are not unique worldwide (thousands of names appear more than once,
 * e.g. KAT is a navaid in Nigeria, Australia and Sri Lanka), so a name maps to all its
 * candidates and {@link #nearest} picks the one closest to where the route already is.
 * Airport ICAO codes are unique.
 */
public final class GeoIndex {

    private final Map<String, List<GeoPoint>> points;
    private final Map<String, GeoPoint> airports;

    private GeoIndex(Map<String, List<GeoPoint>> points, Map<String, GeoPoint> airports) {
        this.points = points;
        this.airports = airports;
    }

    public static GeoIndex build(List<GeoPoint> fixes, List<GeoPoint> navaids, List<GeoPoint> airports) {
        Map<String, List<GeoPoint>> points = new HashMap<>(fixes.size() + navaids.size());
        Map<String, GeoPoint> byIcao = new HashMap<>(airports.size());
        for (GeoPoint p : airports) byIcao.putIfAbsent(p.name(), p);
        for (List<GeoPoint> list : List.of(fixes, navaids, airports)) {
            for (GeoPoint p : list) points.computeIfAbsent(p.name(), k -> new ArrayList<>(1)).add(p);
        }
        return new GeoIndex(points, byIcao);
    }

    public GeoPoint airport(String icao) {
        return icao == null ? null : airports.get(icao);
    }

    public boolean knows(String name) {
        return points.containsKey(name);
    }

    public List<GeoPoint> candidates(String name) {
        return points.getOrDefault(name, List.of());
    }

    /** The point called {@code name} closest to {@code near}; the first candidate if {@code near} is null. */
    public GeoPoint nearest(String name, LatLon near) {
        List<GeoPoint> list = candidates(name);
        if (list.isEmpty()) return null;
        if (near == null || list.size() == 1) return list.getFirst();
        return list.stream().min(Comparator.comparingDouble(p -> p.distanceNm(near))).orElseThrow();
    }
}
