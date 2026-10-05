import { Airway } from '../api';

interface Props {
  airways: Airway[];
  highlighted?: string;
  onHighlight: (name?: string) => void;
}

export function AirwayList({ airways, highlighted, onHighlight }: Props) {
  if (airways.length === 0) return <p className="empty">No airways loaded.</p>;
  return (
    <ul className="list" aria-label="Airways">
      {airways.map((a) => (
        <li key={a.name}>
          <button
            className={a.name === highlighted ? 'item selected' : 'item'}
            onClick={() => onHighlight(a.name === highlighted ? undefined : a.name)}
          >
            <strong>{a.name}</strong>
            <small>{a.points.length} points</small>
          </button>
        </li>
      ))}
    </ul>
  );
}
