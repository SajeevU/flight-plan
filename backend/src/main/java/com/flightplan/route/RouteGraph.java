package com.flightplan.route;

import com.flightplan.geo.GeoPoint;
import com.flightplan.geo.LatLon;
import com.flightplan.route.RoutePoint.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Network of known route segments: consecutive airway vertices plus consecutive points of
 * every filed route. Nodes are keyed by rounded coordinates, so the same fix reached from
 * different datasets becomes one node.
 */
public final class RouteGraph {

    private record Node(String key, String name, double lat, double lon, Map<String, String> edges) implements LatLon {}

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, String> nameAt = new HashMap<>();

    public static RouteGraph build(GeoIndex geo, List<List<RoutePoint>> routes) {
        RouteGraph g = new RouteGraph();
        geo.points().values().forEach(p -> g.nameAt.put(p.key(), p.name()));
        geo.airways().forEach((airway, vertices) -> {
            for (int i = 1; i < vertices.size(); i++) g.link(vertices.get(i - 1), vertices.get(i), airway);
        });
        for (List<RoutePoint> route : routes) {
            for (int i = 1; i < route.size(); i++) g.link(route.get(i - 1), route.get(i), route.get(i - 1).airway());
        }
        return g;
    }

    private Node node(LatLon p, String fallbackName) {
        return nodes.computeIfAbsent(p.key(), k -> new Node(k, nameAt.getOrDefault(k, fallbackName), p.lat(), p.lon(), new HashMap<>()));
    }

    private void link(LatLon a, LatLon b, String airway) {
        Node na = node(a, nameOf(a));
        Node nb = node(b, nameOf(b));
        if (na == nb) return;
        na.edges().put(nb.key(), airway);
        nb.edges().put(na.key(), airway);
    }

    private static String nameOf(LatLon p) {
        return p instanceof GeoPoint g ? g.name() : p instanceof RoutePoint r ? r.name() : p.key();
    }

    /**
     * Proposes a route between the same airports that avoids every intermediate waypoint of
     * the filed route. Empty when the known network has none.
     */
    public Optional<List<RoutePoint>> alternateTo(List<RoutePoint> filed) {
        if (filed.size() < 2) return Optional.empty();
        Set<String> avoid = new HashSet<>();
        for (RoutePoint p : filed.subList(1, filed.size() - 1)) avoid.add(p.key());
        return shortestPath(filed.getFirst(), filed.getLast(), avoid);
    }

    /** A* search with a great-circle distance heuristic that never passes through {@code avoid}. */
    public Optional<List<RoutePoint>> shortestPath(LatLon from, LatLon to, Set<String> avoid) {
        Node start = nodes.get(from.key());
        Node goal = nodes.get(to.key());
        if (start == null || goal == null) return Optional.empty();

        record Open(String key, double f) {}
        Map<String, Double> g = new HashMap<>(Map.of(start.key(), 0.0));
        Map<String, String> cameFrom = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>((x, y) -> Double.compare(x.f(), y.f()));
        open.add(new Open(start.key(), start.distanceNm(goal)));

        while (!open.isEmpty()) {
            Open current = open.poll();
            if (current.key().equals(goal.key())) return Optional.of(reconstruct(cameFrom, current.key()));
            Node node = nodes.get(current.key());
            double gHere = g.get(current.key());
            if (current.f() > gHere + node.distanceNm(goal) + 1e-9) continue; // stale queue entry
            for (String next : node.edges().keySet()) {
                if (avoid.contains(next) && !next.equals(goal.key())) continue;
                Node nextNode = nodes.get(next);
                double tentative = gHere + node.distanceNm(nextNode);
                if (tentative < g.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    g.put(next, tentative);
                    cameFrom.put(next, current.key());
                    open.add(new Open(next, tentative + nextNode.distanceNm(goal)));
                }
            }
        }
        return Optional.empty();
    }

    private List<RoutePoint> reconstruct(Map<String, String> cameFrom, String end) {
        List<String> keys = new ArrayList<>(List.of(end));
        while (cameFrom.containsKey(keys.getLast())) keys.add(cameFrom.get(keys.getLast()));
        Collections.reverse(keys);
        List<RoutePoint> path = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            Node n = nodes.get(keys.get(i));
            String airway = i < keys.size() - 1 ? n.edges().get(keys.get(i + 1)) : null;
            Kind kind = i == 0 || i == keys.size() - 1 ? Kind.AIRPORT : Kind.WAYPOINT;
            path.add(new RoutePoint(n.name(), n.lat(), n.lon(), kind, airway));
        }
        return path;
    }
}
