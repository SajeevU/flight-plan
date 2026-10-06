package com.flightplan.route;

import com.flightplan.caas.FlightObject;
import com.flightplan.caas.FlightObject.RouteElement;
import com.flightplan.geo.GeoPoint;
import com.flightplan.geo.LatLon;
import com.flightplan.route.RoutePoint.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a flight plan into an ordered list of coordinates: departure aerodrome, each filed
 * route element (by seqNum), then destination aerodrome. Where an element continues on an
 * airway, the airway's intermediate points up to the next element are inserted so the drawn
 * line follows the airway instead of cutting straight across.
 */
public final class RouteResolver {

    /** How close (NM) a route point must be to an airway vertex to count as on it. */
    static final double ON_AIRWAY_TOLERANCE_NM = 2;

    /**
     * @param points     the route, in flying order
     * @param unresolved designators in the flight plan that could not be placed on the map
     */
    public record ResolvedRoute(List<RoutePoint> points, List<String> unresolved) {}

    private RouteResolver() {}

    public static ResolvedRoute resolve(FlightObject flight, GeoIndex geo) {
        List<RoutePoint> points = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        addAirport(flight.departureCode(), geo, points, unresolved);

        List<RouteElement> elements = flight.routeElements().stream()
                .sorted(Comparator.comparing(e -> e.seqNum() == null ? 0 : e.seqNum()))
                .toList();
        String pendingAirway = null;
        for (RouteElement el : elements) {
            RoutePoint point = locate(el, geo);
            if (point == null) {
                String name = el.position() == null ? null : el.position().designatedPoint();
                if (name != null) unresolved.add(name);
                continue;
            }
            RoutePoint prev = points.isEmpty() ? null : points.getLast();
            if (prev != null && pendingAirway != null) {
                points.addAll(airwaySegment(geo.airways().get(pendingAirway), prev, point, pendingAirway));
            }
            if (!point.samePlace(prev)) points.add(point);
            pendingAirway = el.airway();
        }

        addAirport(flight.destinationCode(), geo, points, unresolved);
        return new ResolvedRoute(points, unresolved);
    }

    /** Coordinates carried in the flight plan win; otherwise look the designator up. */
    private static RoutePoint locate(RouteElement el, GeoIndex geo) {
        var pos = el.position();
        if (pos == null) return null;
        String name = pos.designatedPoint();
        if (pos.lat() != null && pos.lon() != null) {
            String label = name != null ? name : pos.lat() + "," + pos.lon();
            return new RoutePoint(label, pos.lat(), pos.lon(), Kind.WAYPOINT, el.airway());
        }
        GeoPoint known = name == null ? null : geo.points().get(name);
        return known == null ? null : new RoutePoint(name, known.lat(), known.lon(), Kind.WAYPOINT, el.airway());
    }

    private static void addAirport(String code, GeoIndex geo, List<RoutePoint> points, List<String> unresolved) {
        if (code == null) return;
        GeoPoint p = geo.points().get(code);
        if (p == null) {
            unresolved.add(code);
            return;
        }
        if (!points.isEmpty() && points.getLast().samePlace(p)) return;
        points.add(new RoutePoint(code, p.lat(), p.lon(), Kind.AIRPORT, null));
    }

    /** Intermediate airway vertices strictly between {@code from} and {@code to}, in flying order. */
    static List<RoutePoint> airwaySegment(List<GeoPoint> airway, LatLon from, LatLon to, String airwayName) {
        if (airway == null || airway.size() < 3) return List.of();
        int i = nearestIndex(airway, from);
        int j = nearestIndex(airway, to);
        if (i < 0 || j < 0 || Math.abs(i - j) < 2) return List.of();
        List<GeoPoint> slice = i < j ? airway.subList(i + 1, j) : airway.subList(j + 1, i).reversed();
        return slice.stream()
                .map(p -> new RoutePoint(airwayName, p.lat(), p.lon(), Kind.AIRWAY, airwayName))
                .toList();
    }

    private static int nearestIndex(List<GeoPoint> list, LatLon target) {
        int best = -1;
        double bestDist = ON_AIRWAY_TOLERANCE_NM;
        for (int k = 0; k < list.size(); k++) {
            double d = list.get(k).distanceNm(target);
            if (d <= bestDist) {
                best = k;
                bestDist = d;
            }
        }
        return best;
    }
}
