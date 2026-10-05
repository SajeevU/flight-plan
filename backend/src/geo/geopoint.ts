export interface GeoPoint {
  name: string;
  lat: number;
  lon: number;
}

// The aeronautical data service returns entries as "NAME (lat,lon)",
// e.g. "WSSL (1.42,103.87)". Positive values are North / East.
const ENTRY = /^\s*(\S+)\s*\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)\s*$/;

export function parseGeoPoint(entry: unknown): GeoPoint | null {
  if (typeof entry !== 'string') return null;
  const match = ENTRY.exec(entry);
  if (!match) return null;
  const lat = Number(match[2]);
  const lon = Number(match[3]);
  if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
  return { name: match[1], lat, lon };
}

export function parseGeoPoints(entries: unknown): GeoPoint[] {
  if (!Array.isArray(entries)) return [];
  return entries.map(parseGeoPoint).filter((p): p is GeoPoint => p !== null);
}

const EARTH_RADIUS_NM = 3440.065;

/** Great-circle distance in nautical miles. */
export function distanceNm(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_NM * Math.asin(Math.min(1, Math.sqrt(h)));
}
