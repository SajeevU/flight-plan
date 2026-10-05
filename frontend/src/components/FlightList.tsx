import { FlightSummary } from '../api';

interface Props {
  flights: FlightSummary[];
  selectedId?: string;
  onSelect: (flight: FlightSummary) => void;
}

export function FlightList({ flights, selectedId, onSelect }: Props) {
  if (flights.length === 0) return <p className="empty">No flights match.</p>;
  return (
    <ul className="list" aria-label="Flights">
      {flights.map((f) => (
        <li key={f.id}>
          <button className={f.id === selectedId ? 'item selected' : 'item'} onClick={() => onSelect(f)}>
            <strong>{f.callsign}</strong>
            <span>
              {f.departure ?? '?'} → {f.destination ?? '?'}
            </span>
            <small>
              {[f.aircraftType, f.dateOfFlight, f.estimatedOffBlockTime && `EOBT ${f.estimatedOffBlockTime}`].filter(Boolean).join(' · ')}
            </small>
          </button>
        </li>
      ))}
    </ul>
  );
}
