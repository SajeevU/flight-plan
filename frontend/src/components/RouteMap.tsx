import { useEffect, useMemo } from 'react';
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet';
import type { LatLngTuple } from 'leaflet';
import { Airway, RoutePoint } from '../api';

interface Props {
  route?: RoutePoint[];
  alternate?: RoutePoint[];
  airways: Airway[];
  highlightedAirway?: string;
}

const toLatLng = (p: { lat: number; lon: number }): LatLngTuple => [p.lat, p.lon];

function FitBounds({ points }: { points: LatLngTuple[] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length > 1) map.fitBounds(points, { padding: [40, 40] });
    else if (points.length === 1) map.setView(points[0], 7);
  }, [map, points]);
  return null;
}

const NONE: RoutePoint[] = [];

export function RouteMap({ route = NONE, alternate = NONE, airways, highlightedAirway }: Props) {
  const highlighted = airways.find((a) => a.name === highlightedAirway);
  // Memoised so the map only re-fits when what is shown actually changes.
  const focus = useMemo(
    () => (route.length ? [...route, ...alternate].map(toLatLng) : (highlighted?.points.map(toLatLng) ?? [])),
    [route, alternate, highlighted],
  );

  return (
    <MapContainer center={[5, 105]} zoom={4} worldCopyJump className="map">
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {airways.map((a) => (
        <Polyline
          key={a.name}
          positions={a.points.map(toLatLng)}
          pathOptions={a.name === highlightedAirway ? { color: '#7c3aed', weight: 4 } : { color: '#94a3b8', weight: 1, opacity: 0.6 }}
        >
          <Tooltip sticky>{a.name}</Tooltip>
        </Polyline>
      ))}
      {alternate.length > 1 && (
        <Polyline positions={alternate.map(toLatLng)} pathOptions={{ color: '#ea580c', weight: 4, dashArray: '8 8' }} />
      )}
      {route.length > 1 && <Polyline positions={route.map(toLatLng)} pathOptions={{ color: '#2563eb', weight: 4 }} />}
      {[...route, ...alternate]
        .filter((p) => p.kind !== 'airway')
        .map((p, i) => (
          <CircleMarker
            key={`${p.name}-${i}`}
            center={toLatLng(p)}
            radius={p.kind === 'airport' ? 7 : 4}
            pathOptions={{ color: p.kind === 'airport' ? '#0f172a' : '#2563eb', fillOpacity: 1 }}
          >
            <Tooltip>
              {p.name}
              {p.airway ? ` → ${p.airway}` : ''}
            </Tooltip>
          </CircleMarker>
        ))}
      <FitBounds points={focus} />
    </MapContainer>
  );
}
