import { RouteGraph, findAlternateRoute } from './alternate-route';
import { RoutePoint, buildGeoIndex } from './route.resolver';

// Two parallel airways from DEP to ARR: north (via N) and south (via S).
const geo = buildGeoIndex(
  [
    { name: 'N', lat: 1, lon: 1 },
    { name: 'S', lat: -1.5, lon: 1 },
  ],
  [
    { name: 'DEP', lat: 0, lon: 0 },
    { name: 'ARR', lat: 0, lon: 2 },
  ],
  [
    { name: 'NORTH', lat: 0, lon: 0 },
    { name: 'NORTH', lat: 1, lon: 1 },
    { name: 'NORTH', lat: 0, lon: 2 },
    { name: 'SOUTH', lat: 0, lon: 0 },
    { name: 'SOUTH', lat: -1.5, lon: 1 },
    { name: 'SOUTH', lat: 0, lon: 2 },
  ],
);

const p = (name: string, lat: number, lon: number): RoutePoint => ({ name, lat, lon, kind: 'waypoint' });

describe('findAlternateRoute', () => {
  const graph = RouteGraph.build(geo, []);

  it('finds the shortest path when nothing is avoided', () => {
    const path = graph.shortestPath({ lat: 0, lon: 0 }, { lat: 0, lon: 2 }, new Set());
    expect(path?.map((x) => x.name)).toEqual(['DEP', 'N', 'ARR']);
    expect(path?.[0].airway).toBe('NORTH');
  });

  it('routes around the filed waypoints', () => {
    const filed = [p('DEP', 0, 0), p('N', 1, 1), p('ARR', 0, 2)];
    expect(findAlternateRoute(filed, graph)?.map((x) => x.name)).toEqual(['DEP', 'S', 'ARR']);
  });

  it('returns null when no alternative exists', () => {
    const filed = [p('DEP', 0, 0), p('N', 1, 1), p('S', -1.5, 1), p('ARR', 0, 2)];
    expect(findAlternateRoute(filed, graph)).toBeNull();
  });

  it('returns null for unknown endpoints or too-short routes', () => {
    expect(findAlternateRoute([p('X', 50, 50), p('Y', 51, 51)], graph)).toBeNull();
    expect(findAlternateRoute([p('DEP', 0, 0)], graph)).toBeNull();
  });
});
