export interface FlightSummary {
  id: string;
  callsign: string;
  departure?: string;
  destination?: string;
  aircraftType?: string;
  dateOfFlight?: string;
  estimatedOffBlockTime?: string;
}

export interface RoutePoint {
  name: string;
  lat: number;
  lon: number;
  kind: 'airport' | 'waypoint' | 'airway';
  airway?: string;
}

export interface FlightRoute {
  flight: FlightSummary;
  points: RoutePoint[];
  unresolved: string[];
}

export interface Airway {
  name: string;
  points: { lat: number; lon: number }[];
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { headers: { accept: 'application/json' } });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  flights: (callsign = '') => getJson<FlightSummary[]>(`/api/flights${callsign ? `?callsign=${encodeURIComponent(callsign)}` : ''}`),
  route: (id: string) => getJson<FlightRoute>(`/api/flights/${encodeURIComponent(id)}/route`),
  alternateRoute: (id: string) => getJson<FlightRoute>(`/api/flights/${encodeURIComponent(id)}/alternate-route`),
  airways: () => getJson<Airway[]>('/api/airways'),
  health: () => getJson<{ dataSource: string }>('/api/health'),
};
