package com.flightplan.route;

import com.flightplan.geo.GeoPoint;
import com.flightplan.route.RoutePoint.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An airway as published: its name and its fixes in order, e.g. "A464: [CMA,TOPAS,BEKOD,...]". */
public record Airway(String name, List<String> fixes) {

    private static final Pattern ENTRY = Pattern.compile("^\\s*([^:\\s]+)\\s*:\\s*\\[(.*)]\\s*$");

    /** Parses one search result entry, or returns null when it isn't in the expected format. */
    public static Airway parse(String entry) {
        if (entry == null) return null;
        Matcher m = ENTRY.matcher(entry);
        if (!m.matches()) return null;
        List<String> fixes = Arrays.stream(m.group(2).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new Airway(m.group(1), fixes);
    }

    /**
     * Fixes strictly between {@code from} and {@code to} along this airway, in flying order
     * (airways can be flown in either direction). Empty when either isn't on the airway.
     */
    public List<String> between(String from, String to) {
        int i = fixes.indexOf(from);
        int j = fixes.indexOf(to);
        if (i < 0 || j < 0 || Math.abs(i - j) < 2) return List.of();
        return i < j ? fixes.subList(i + 1, j) : fixes.subList(j + 1, i).reversed();
    }

    /**
     * The airway's fixes placed on the map. Names repeat worldwide, so it starts from the first
     * fix whose name is unique and walks outwards, taking each next fix nearest the previous one.
     */
    public List<RoutePoint> path(GeoIndex geo) {
        int anchor = 0;
        for (int k = 0; k < fixes.size(); k++) {
            if (geo.candidates(fixes.get(k)).size() == 1) {
                anchor = k;
                break;
            }
        }
        GeoPoint start = fixes.isEmpty() ? null : geo.nearest(fixes.get(anchor), null);
        if (start == null) return List.of();
        List<RoutePoint> forward = walk(geo, start, fixes.subList(anchor + 1, fixes.size()));
        List<RoutePoint> backward = walk(geo, start, fixes.subList(0, anchor).reversed());
        Collections.reverse(backward);
        List<RoutePoint> path = new ArrayList<>(backward);
        path.add(point(start));
        path.addAll(forward);
        // Each point carries the airway to the next one, which the route graph uses for its edges.
        return path.stream().map(p -> p.withAirway(name)).toList();
    }

    private List<RoutePoint> walk(GeoIndex geo, GeoPoint from, List<String> names) {
        List<RoutePoint> out = new ArrayList<>();
        GeoPoint prev = from;
        for (String fix : names) {
            GeoPoint p = geo.nearest(fix, prev);
            if (p == null) continue;
            out.add(point(p));
            prev = p;
        }
        return out;
    }

    private static RoutePoint point(GeoPoint p) {
        return new RoutePoint(p.name(), p.lat(), p.lon(), Kind.WAYPOINT, null);
    }
}
