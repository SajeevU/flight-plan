import { useEffect, useState } from 'react';
import { Airway, FlightRoute, FlightSummary, api } from './api';
import { AirwayList } from './components/AirwayList';
import { FlightList } from './components/FlightList';
import { RouteMap } from './components/RouteMap';

type Tab = 'flights' | 'airways';

export default function App() {
  const [tab, setTab] = useState<Tab>('flights');
  const [search, setSearch] = useState('');
  const [flights, setFlights] = useState<FlightSummary[]>([]);
  const [airwayNames, setAirwayNames] = useState<string[]>([]);
  const [selected, setSelected] = useState<FlightSummary>();
  const [route, setRoute] = useState<FlightRoute>();
  const [alternate, setAlternate] = useState<FlightRoute>();
  const [airway, setAirway] = useState<Airway>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<string>();

  // Debounced callsign search.
  useEffect(() => {
    const t = setTimeout(() => {
      api
        .flights(search.trim())
        .then(setFlights)
        .catch((e: Error) => setError(e.message));
    }, 250);
    return () => clearTimeout(t);
  }, [search]);

  useEffect(() => {
    api
      .health()
      .then((h) => setDataSource(h.dataSource))
      .catch(() => undefined);
    api
      .airwayNames()
      .then(setAirwayNames)
      .catch((e: Error) => setError(e.message));
  }, []);

  const selectFlight = async (flight: FlightSummary) => {
    setSelected(flight);
    setAlternate(undefined);
    setAirway(undefined);
    setError(undefined);
    setLoading(true);
    try {
      setRoute(await api.route(flight.id));
    } catch (e) {
      setRoute(undefined);
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const selectAirway = async (name: string) => {
    setRoute(undefined);
    setAlternate(undefined);
    setSelected(undefined);
    setError(undefined);
    if (airway?.name === name) return setAirway(undefined);
    try {
      setAirway(await api.airway(name));
    } catch (e) {
      setAirway(undefined);
      setError((e as Error).message);
    }
  };

  const toggleAlternate = async () => {
    if (!selected) return;
    if (alternate) return setAlternate(undefined);
    setError(undefined);
    setLoading(true);
    try {
      setAlternate(await api.alternateRoute(selected.id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <h1>Flight Plan Viewer</h1>
        {dataSource && dataSource !== 'caas' && (
          <p className="source" title="The CAAS API is not being used, so the bundled sample data is shown.">
            Sample data: {dataSource}
          </p>
        )}
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === 'flights'} onClick={() => setTab('flights')}>
            Flights ({flights.length})
          </button>
          <button role="tab" aria-selected={tab === 'airways'} onClick={() => setTab('airways')}>
            Airways ({airwayNames.length})
          </button>
        </div>
        {tab === 'flights' ? (
          <>
            <input
              type="search"
              placeholder="Search by callsign, e.g. SIA"
              aria-label="Search by callsign"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <FlightList flights={flights} selectedId={selected?.id} onSelect={selectFlight} />
          </>
        ) : (
          <AirwayList names={airwayNames} selected={airway?.name} onSelect={selectAirway} />
        )}
      </aside>

      <main className="content">
        {route && (
          <section className="details" aria-label="Route details">
            <div>
              <strong>{route.flight.callsign}</strong> {route.flight.departure} → {route.flight.destination}
              <div className="route-text">{describe(route)}</div>
              {alternate && <div className="route-text alt">Alternate: {describe(alternate)}</div>}
              {route.unresolved.length > 0 && <div className="warn">Could not locate: {route.unresolved.join(', ')}</div>}
            </div>
            <button onClick={toggleAlternate} disabled={loading}>
              {alternate ? 'Hide alternate route' : 'Show alternate route'}
            </button>
          </section>
        )}
        {error && (
          <div role="alert" className="error">
            {error}
          </div>
        )}
        {airway && (
          <section className="details" aria-label="Airway details">
            <div>
              <strong>{airway.name}</strong> {airway.fixes.length} fixes
              <div className="route-text">{airway.fixes.join(' ')}</div>
            </div>
          </section>
        )}
        <RouteMap route={route?.points} alternate={alternate?.points} airway={airway?.points} />
      </main>
    </div>
  );
}

/** ICAO-style route string, e.g. "WSSS VJR A464 ATMAX WMKK". */
function describe(route: FlightRoute): string {
  return route.points
    .filter((p) => p.kind !== 'airway')
    .map((p) => (p.airway ? `${p.name} ${p.airway}` : p.name))
    .join(' ');
}
