package com.flightplan.route;

import com.flightplan.geo.GeoPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup tables built from the aeronautical datasets.
 *
 * @param points  fixes and airports keyed by designator
 * @param airways airways keyed by name, points in published order
 */
public record GeoIndex(Map<String, GeoPoint> points, Map<String, List<GeoPoint>> airways) {

    public static GeoIndex build(List<GeoPoint> fixes, List<GeoPoint> airports, List<GeoPoint> airwayPoints) {
        Map<String, GeoPoint> points = new LinkedHashMap<>();
        for (GeoPoint p : airports) points.putIfAbsent(p.name(), p);
        for (GeoPoint p : fixes) points.putIfAbsent(p.name(), p);
        // The airways list repeats the airway name once per point, in order.
        Map<String, List<GeoPoint>> airways = new LinkedHashMap<>();
        for (GeoPoint p : airwayPoints) airways.computeIfAbsent(p.name(), k -> new ArrayList<>()).add(p);
        return new GeoIndex(points, airways);
    }
}
