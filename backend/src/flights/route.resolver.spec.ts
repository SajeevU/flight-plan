import { FlightObject } from '../caas/flight-object';
import { buildGeoIndex, resolveRoute } from './route.resolver';

const geo = buildGeoIndex(
  [
    { name: 'AAA', lat: 1, lon: 1 },
    { name: 'BBB', lat: 2, lon: 2 },
    { name: 'CCC', lat: 3, lon: 3 },
  ],
  [
    { name: 'DEP', lat: 0, lon: 0 },
    { name: 'ARR', lat: 4, lon: 4 },
  ],
  [
    { name: 'X1', lat: 1, lon: 1 },
    { name: 'X1', lat: 2, lon: 2 },
    { name: 'X1', lat: 3, lon: 3 },
  ],
);

const flight = (route: FlightObject['filedRoute']): FlightObject => ({
  aircraftIdentification: 'TST1',
  departure: { departureAerodrome: 'DEP' },
  arrival: { destinationAerodrome: { locationIndicator: 'ARR' } },
  filedRoute: route,
});

describe('resolveRoute', () => {
  it('orders departure, route elements by seqNum, then destination', () => {
    const { points, unresolved } = resolveRoute(
      flight({ routeElement: [{ seqNum: 2, position: { designatedPoint: 'CCC' } }, { seqNum: 1, position: { designatedPoint: 'AAA' } }] }),
      geo,
    );
    expect(points.map((p) => p.name)).toEqual(['DEP', 'AAA', 'CCC', 'ARR']);
    expect(unresolved).toEqual([]);
  });

  it('expands the airway between two route points', () => {
    const { points } = resolveRoute(
      flight({ routeElement: [{ seqNum: 1, position: { designatedPoint: 'AAA' }, airway: 'X1' }, { seqNum: 2, position: { designatedPoint: 'CCC' } }] }),
      geo,
    );
    expect(points.map((p) => [p.lat, p.kind])).toEqual([
      [0, 'airport'],
      [1, 'waypoint'],
      [2, 'airway'],
      [3, 'waypoint'],
      [4, 'airport'],
    ]);
    expect(points[1].airway).toBe('X1');
  });

  it('prefers coordinates carried in the flight plan', () => {
    const { points } = resolveRoute(flight({ routeElement: [{ position: { designatedPoint: 'AAA', lat: 9, lon: 9 } }] }), geo);
    expect(points[1]).toMatchObject({ name: 'AAA', lat: 9, lon: 9 });
  });

  it('reports designators it cannot place', () => {
    const f = flight({ routeElement: [{ position: { designatedPoint: 'NOPE' } }] });
    f.arrival = { destinationAerodrome: 'ZZZZ' };
    expect(resolveRoute(f, geo).unresolved).toEqual(['NOPE', 'ZZZZ']);
  });

  it('copes with a flight with no route at all', () => {
    expect(resolveRoute({}, geo)).toEqual({ points: [], unresolved: [] });
  });
});
