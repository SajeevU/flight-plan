package com.flightplan.route;

import com.flightplan.caas.FlightObject;
import com.flightplan.caas.FlightObject.RouteElement;
import com.flightplan.geo.GeoPoint;
import com.flightplan.geo.LatLon;
import com.flightplan.route.RoutePoint.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns a flight plan into an ordered list of coordinates: departure aerodrome, each filed
 * route element (by seqNum), then destination aerodrome.
 *
 * <p>Each element names a point and the airway flown from it to the next element. The
 * airway's fixes between the two are inserted, so the drawn line follows the airway instead
 * of cutting straight across. Because point names repeat worldwide, every name is resolved
 * to the candidate nearest the previous point on the route.
 */
public final class RouteResolver {

    /** ATS route designators such as A464, UL333, Y340. Excludes DCT and lat/lon text like 42N160W. */
    private static final Pattern AIRWAY = Pattern.compile("^[A-Z]{1,2}\\d{1,4}[A-Z]?$");

    /**
     * @param points     the route, in flying order
     * @param unresolved designators in the flight plan that could not be placed on the map
     */
    public record ResolvedRoute(List<RoutePoint> points, List<String> unresolved) {}

    private RouteResolver() {}

    /**
     * @param airways looks an airway up by name; returns null when unknown
     */
    public static ResolvedRoute resolve(FlightObject flight, GeoIndex geo, Function<String, Airway> airways) {
        List<RoutePoint> points = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        GeoPoint dep = geo.airport(flight.departureCode());
        GeoPoint dest = geo.airport(flight.destinationCode());
        if (dep != null) points.add(new RoutePoint(dep.name(), dep.lat(), dep.lon(), Kind.AIRPORT, null));
        else if (flight.departureCode() != null) unresolved.add(flight.departureCode());
        LatLon anchor = dep != null ? dep : dest;

        String prevName = null;
        String pendingAirway = null;
        for (RouteElement el : sorted(flight.routeElements())) {
            String name = el.position() == null ? null : el.position().designatedPoint();
            LatLon near = points.isEmpty() ? anchor : points.getLast();
            RoutePoint point = locate(el, name, geo, near);
            if (point == null) {
                if (name != null) unresolved.add(name);
                continue;
            }
            if (pendingAirway != null && prevName != null && name != null) {
                Airway airway = airways.apply(pendingAirway);
                if (airway != null) {
                    for (String fix : airway.between(prevName, name)) {
                        GeoPoint p = geo.nearest(fix, points.isEmpty() ? anchor : points.getLast());
                        if (p != null) add(points, new RoutePoint(fix, p.lat(), p.lon(), Kind.AIRWAY, pendingAirway));
                    }
                }
            }
            String airway = airwayName(el.airway());
            add(points, airway == null ? point : point.withAirway(airway));
            prevName = name;
            pendingAirway = airway;
        }

        if (dest != null) add(points, new RoutePoint(dest.name(), dest.lat(), dest.lon(), Kind.AIRPORT, null));
        else if (flight.destinationCode() != null) unresolved.add(flight.destinationCode());
        return new ResolvedRoute(points, unresolved);
    }

    /** Distinct airway names a flight plan refers to, so they can be fetched up front. */
    public static List<String> airwaysOf(FlightObject flight) {
        return flight.routeElements().stream().map(e -> airwayName(e.airway())).filter(a -> a != null).distinct().toList();
    }

    /** "P570" stays "P570"; "M300/N0486F410" (airway plus speed/level change) becomes "M300"; "DCT" is null. */
    public static String airwayName(String raw) {
        if (raw == null) return null;
        String name = raw.split("/", 2)[0].trim();
        return AIRWAY.matcher(name).matches() ? name : null;
    }

    private static List<RouteElement> sorted(List<RouteElement> elements) {
        return elements.stream().sorted(Comparator.comparing(e -> e.seqNum() == null ? 0 : e.seqNum())).toList();
    }

    /** Coordinates carried in the flight plan win; otherwise the nearest point with that name. */
    private static RoutePoint locate(RouteElement el, String name, GeoIndex geo, LatLon near) {
        var pos = el.position();
        if (pos == null) return null;
        if (pos.lat() != null && pos.lon() != null) {
            String label = name != null ? name : pos.lat() + "," + pos.lon();
            return new RoutePoint(label, pos.lat(), pos.lon(), Kind.WAYPOINT, null);
        }
        GeoPoint p = name == null ? null : geo.nearest(name, near);
        return p == null ? null : new RoutePoint(name, p.lat(), p.lon(), Kind.WAYPOINT, null);
    }

    private static void add(List<RoutePoint> points, RoutePoint p) {
        if (!points.isEmpty() && points.getLast().samePlace(p)) return;
        points.add(p);
    }
}
