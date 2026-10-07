import { useMemo, useState } from 'react';

interface Props {
  names: string[];
  selected?: string;
  onSelect: (name: string) => void;
}

// There are ~9,000 airways; rendering them all at once makes the list sluggish.
const MAX_SHOWN = 200;

export function AirwayList({ names, selected, onSelect }: Props) {
  const [filter, setFilter] = useState('');
  const matches = useMemo(() => {
    const term = filter.trim().toUpperCase();
    return term ? names.filter((n) => n.includes(term)) : names;
  }, [names, filter]);

  return (
    <>
      <input
        type="search"
        placeholder="Filter airways, e.g. A464"
        aria-label="Filter airways"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
      />
      {matches.length === 0 ? (
        <p className="empty">No airways match.</p>
      ) : (
        <ul className="list" aria-label="Airways">
          {matches.slice(0, MAX_SHOWN).map((name) => (
            <li key={name}>
              <button className={name === selected ? 'item selected' : 'item'} onClick={() => onSelect(name)}>
                <strong>{name}</strong>
              </button>
            </li>
          ))}
          {matches.length > MAX_SHOWN && (
            <li className="empty">
              Showing {MAX_SHOWN} of {matches.length}. Type to narrow down.
            </li>
          )}
        </ul>
      )}
    </>
  );
}
