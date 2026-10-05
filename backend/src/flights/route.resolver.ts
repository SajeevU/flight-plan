import { FlightObject, aerodromeCode } from '../caas/flight-object';
import { GeoPoint, distanceNm } from '../geo/geopoint';

export type RoutePointKind = 'airport' | 'waypoint' | 'airway';

export interface RoutePoint {
  name: string;
  lat: number;
  lon: number;
  kind: RoutePointKind;
  /** Airway flown from this point to the next one, if any. */
  airway?: string;
}

export interface ResolvedRoute {
  points: RoutePoint[];
  /** Designators in the flight plan we could not place on the map. */
  unresolved: string[];
}

export interface GeoIndex {
  /** Fixes and airports keyed by designator. */
  points: Map<string, GeoPoint>;
  /** Airways keyed by name, points in published order. */
  airways: Map<string, GeoPoint[]>;
}

export function buildGeoIndex(fixes: GeoPoint[], airports: GeoPoint[], airwayPoints: GeoPoint[]): GeoIndex {
  const points = new Map<string, GeoPoint>();
  for (const p of [...airports, ...fixes]) if (!points.has(p.name)) points.set(p.name, p);
  const airways = new Map<string, GeoPoint[]>();
  for (const p of airwayPoints) {
    const list = airways.get(p.name) ?? [];
    list.push(p);
    airways.set(p.name, list);
  }
  return { points, airways };
}

// How close (NM) a route point must be to an airway vertex to count as on it.
const ON_AIRWAY_TOLERANCE_NM = 2;

/**
 * Turns a flight plan into an ordered list of coordinates:
 * departure aerodrome, each filed route element, then destination aerodrome.
 * Where an element says it continues on an airway, the airway's intermediate
 * points between this element and the next are inserted so the drawn line
 * follows the airway rather than cutting straight across.
 */
export function resolveRoute(flight: FlightObject, geo: GeoIndex): ResolvedRoute {
  const points: RoutePoint[] = [];
  const unresolved: string[] = [];

  const pushAirport = (code: string | undefined) => {
    if (!code) return;
    const p = geo.points.get(code);
    if (!p) return void unresolved.push(code);
    const last = points[points.length - 1];
    if (last && last.lat === p.lat && last.lon === p.lon) return;
    points.push({ name: code, lat: p.lat, lon: p.lon, kind: 'airport' });
  };

  pushAirport(aerodromeCode(flight.departure?.departureAerodrome));

  const elements = [...(flight.filedRoute?.routeElement ?? [])].sort((a, b) => (a.seqNum ?? 0) - (b.seqNum ?? 0));
  let pendingAirway: string | undefined;
  for (const el of elements) {
    const name = el.position?.designatedPoint;
    let point: RoutePoint | undefined;
    if (typeof el.position?.lat === 'number' && typeof el.position?.lon === 'number') {
      point = { name: name ?? `${el.position.lat},${el.position.lon}`, lat: el.position.lat, lon: el.position.lon, kind: 'waypoint' };
    } else if (name) {
      const p = geo.points.get(name);
      if (p) point = { name, lat: p.lat, lon: p.lon, kind: 'waypoint' };
    }
    if (!point) {
      if (name) unresolved.push(name);
      continue;
    }
    const prev = points[points.length - 1];
    if (prev && pendingAirway) points.push(...airwaySegment(geo.airways.get(pendingAirway), prev, point, pendingAirway));
    if (el.airway) point.airway = el.airway;
    if (!(prev && prev.lat === point.lat && prev.lon === point.lon)) points.push(point);
    pendingAirway = el.airway;
  }

  pushAirport(aerodromeCode(flight.arrival?.destinationAerodrome));
  return { points, unresolved };
}

/** Intermediate airway vertices strictly between `from` and `to`, in flying order. */
export function airwaySegment(
  airway: GeoPoint[] | undefined,
  from: { lat: number; lon: number },
  to: { lat: number; lon: number },
  airwayName: string,
): RoutePoint[] {
  if (!airway || airway.length < 3) return [];
  const i = nearestIndex(airway, from);
  const j = nearestIndex(airway, to);
  if (i < 0 || j < 0 || Math.abs(i - j) < 2) return [];
  const slice = i < j ? airway.slice(i + 1, j) : airway.slice(j + 1, i).reverse();
  return slice.map((p) => ({ name: airwayName, lat: p.lat, lon: p.lon, kind: 'airway' as const, airway: airwayName }));
}

function nearestIndex(list: GeoPoint[], target: { lat: number; lon: number }): number {
  let best = -1;
  let bestDist = ON_AIRWAY_TOLERANCE_NM;
  list.forEach((p, idx) => {
    const d = distanceNm(p, target);
    if (d <= bestDist) {
      best = idx;
      bestDist = d;
    }
  });
  return best;
}
