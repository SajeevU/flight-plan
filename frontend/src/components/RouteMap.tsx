import { useEffect, useMemo } from 'react';
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet';
import type { LatLngTuple } from 'leaflet';
import { RoutePoint } from '../api';

interface Props {
  route?: RoutePoint[];
  alternate?: RoutePoint[];
  airway?: RoutePoint[];
}

const NONE: RoutePoint[] = [];

/**
 * Leaflet draws a line from 179°E to -179°E the long way round the world. Shift each
 * longitude by ±360° so consecutive points stay within 180° of each other.
 */
export function unwrap(points: { lat: number; lon: number }[]): LatLngTuple[] {
  const out: LatLngTuple[] = [];
  let prevLon: number | undefined;
  for (const p of points) {
    let lon = p.lon;
    if (prevLon !== undefined) {
      while (lon - prevLon > 180) lon -= 360;
      while (lon - prevLon < -180) lon += 360;
    }
    out.push([p.lat, lon]);
    prevLon = lon;
  }
  return out;
}

function FitBounds({ points }: { points: LatLngTuple[] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length > 1) map.fitBounds(points, { padding: [40, 40] });
    else if (points.length === 1) map.setView(points[0], 7);
  }, [map, points]);
  return null;
}

function Markers({ points, positions, color }: { points: RoutePoint[]; positions: LatLngTuple[]; color: string }) {
  return (
    <>
      {points.map((p, i) => (
        <CircleMarker
          key={`${p.name}-${i}`}
          center={positions[i]}
          radius={p.kind === 'airport' ? 7 : p.kind === 'airway' ? 3 : 4}
          pathOptions={{ color: p.kind === 'airport' ? '#0f172a' : color, fillOpacity: 1 }}
        >
          <Tooltip>
            {p.name}
            {p.airway ? ` → ${p.airway}` : ''}
          </Tooltip>
        </CircleMarker>
      ))}
    </>
  );
}

export function RouteMap({ route = NONE, alternate = NONE, airway = NONE }: Props) {
  const routeLine = useMemo(() => unwrap(route), [route]);
  // Draw the alternate on the same side of the dateline as the filed route.
  const altLine = useMemo(() => unwrap([...route.slice(0, 1), ...alternate]).slice(route.length ? 1 : 0), [route, alternate]);
  const airwayLine = useMemo(() => unwrap(airway), [airway]);
  // Memoised so the map only re-fits when what is shown actually changes.
  const focus = useMemo(
    () => (routeLine.length ? [...routeLine, ...altLine] : airwayLine),
    [routeLine, altLine, airwayLine],
  );

  return (
    <MapContainer center={[5, 105]} zoom={4} worldCopyJump className="map">
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {airwayLine.length > 1 && <Polyline positions={airwayLine} pathOptions={{ color: '#7c3aed', weight: 4 }} />}
      <Markers points={airway} positions={airwayLine} color="#7c3aed" />
      {altLine.length > 1 && <Polyline positions={altLine} pathOptions={{ color: '#ea580c', weight: 4, dashArray: '8 8' }} />}
      {routeLine.length > 1 && <Polyline positions={routeLine} pathOptions={{ color: '#2563eb', weight: 4 }} />}
      <Markers points={route} positions={routeLine} color="#2563eb" />
      <Markers points={alternate} positions={altLine} color="#ea580c" />
      <FitBounds points={focus} />
    </MapContainer>
  );
}
