import { GeoPoint, distanceNm } from '../geo/geopoint';
import { GeoIndex, RoutePoint } from './route.resolver';

interface Node {
  key: string;
  name: string;
  lat: number;
  lon: number;
  edges: Map<string, string | undefined>; // neighbour key -> airway name
}

/**
 * Network of known route segments: consecutive airway vertices plus consecutive
 * points of every filed route. Nodes are keyed by rounded coordinates so the
 * same fix reached from different sources becomes one node.
 */
export class RouteGraph {
  readonly nodes = new Map<string, Node>();

  static build(geo: GeoIndex, routes: RoutePoint[][]): RouteGraph {
    const graph = new RouteGraph();
    const nameAt = new Map<string, string>();
    for (const p of geo.points.values()) nameAt.set(keyOf(p), p.name);

    for (const [airway, vertices] of geo.airways) {
      for (let i = 1; i < vertices.length; i++) {
        graph.link(vertices[i - 1], vertices[i], airway, nameAt);
      }
    }
    for (const route of routes) {
      for (let i = 1; i < route.length; i++) {
        graph.link(route[i - 1], route[i], route[i - 1].airway, nameAt);
      }
    }
    return graph;
  }

  private node(p: { lat: number; lon: number; name?: string }, nameAt: Map<string, string>): Node {
    const key = keyOf(p);
    let n = this.nodes.get(key);
    if (!n) {
      n = { key, name: nameAt.get(key) ?? p.name ?? key, lat: p.lat, lon: p.lon, edges: new Map() };
      this.nodes.set(key, n);
    }
    return n;
  }

  private link(a: GeoPoint | RoutePoint, b: GeoPoint | RoutePoint, airway: string | undefined, nameAt: Map<string, string>) {
    const na = this.node(a, nameAt);
    const nb = this.node(b, nameAt);
    if (na === nb) return;
    na.edges.set(nb.key, airway);
    nb.edges.set(na.key, airway);
  }

  /**
   * A* search (great-circle distance heuristic) from `from` to `to`
   * that never passes through any node in `avoid`.
   */
  shortestPath(from: { lat: number; lon: number }, to: { lat: number; lon: number }, avoid: Set<string>): RoutePoint[] | null {
    const start = this.nodes.get(keyOf(from));
    const goal = this.nodes.get(keyOf(to));
    if (!start || !goal) return null;

    const g = new Map<string, number>([[start.key, 0]]);
    const cameFrom = new Map<string, string>();
    const open = new Set<string>([start.key]);
    const f = (k: string) => (g.get(k) ?? Infinity) + distanceNm(this.nodes.get(k)!, goal);

    while (open.size > 0) {
      // Linear scan is fine at this graph size; swap for a binary heap if it grows.
      let current = '';
      let best = Infinity;
      for (const k of open) {
        const score = f(k);
        if (score < best) [best, current] = [score, k];
      }
      if (current === goal.key) return this.reconstruct(cameFrom, current);
      open.delete(current);

      const node = this.nodes.get(current)!;
      for (const nextKey of node.edges.keys()) {
        if (avoid.has(nextKey) && nextKey !== goal.key) continue;
        const tentative = g.get(current)! + distanceNm(node, this.nodes.get(nextKey)!);
        if (tentative < (g.get(nextKey) ?? Infinity)) {
          cameFrom.set(nextKey, current);
          g.set(nextKey, tentative);
          open.add(nextKey);
        }
      }
    }
    return null;
  }

  private reconstruct(cameFrom: Map<string, string>, end: string): RoutePoint[] {
    const keys = [end];
    while (cameFrom.has(keys[0])) keys.unshift(cameFrom.get(keys[0])!);
    return keys.map((k, i) => {
      const n = this.nodes.get(k)!;
      const airway = i < keys.length - 1 ? n.edges.get(keys[i + 1]) : undefined;
      const kind = i === 0 || i === keys.length - 1 ? 'airport' : 'waypoint';
      return { name: n.name, lat: n.lat, lon: n.lon, kind, ...(airway ? { airway } : {}) };
    });
  }
}

export function keyOf(p: { lat: number; lon: number }): string {
  return `${p.lat.toFixed(3)},${p.lon.toFixed(3)}`;
}

/**
 * Proposes a route between the same airports that avoids every intermediate
 * waypoint of the filed route. Returns null when the known network has none.
 */
export function findAlternateRoute(filed: RoutePoint[], graph: RouteGraph): RoutePoint[] | null {
  if (filed.length < 2) return null;
  const from = filed[0];
  const to = filed[filed.length - 1];
  const avoid = new Set(filed.slice(1, -1).map(keyOf));
  return graph.shortestPath(from, to, avoid);
}
