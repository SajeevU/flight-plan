import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

// Leaflet needs a real browser; the map itself is covered by the Playwright test.
type Pts = { name: string }[];
vi.mock('./components/RouteMap', () => ({
  RouteMap: ({ route = [], alternate = [], airway = [] }: { route?: Pts; alternate?: Pts; airway?: Pts }) => (
    <div data-testid="map">
      route:{route.map((p) => p.name).join(',')} alt:{alternate.map((p) => p.name).join(',')} airway:
      {airway.map((p) => p.name).join(',')}
    </div>
  ),
}));

const flights = [
  { id: 'f1', callsign: 'SIA200', departure: 'WSSS', destination: 'WMKK' },
  { id: 'f2', callsign: 'MAS604', departure: 'WSSS', destination: 'WMKK' },
];
const point = (name: string, kind = 'waypoint', airway?: string) => ({ name, lat: 1, lon: 1, kind, airway });

function mockFetch() {
  const fetchMock = vi.fn(async (url: string) => {
    const json = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body });
    if (url.startsWith('/api/flights?callsign=')) {
      const term = decodeURIComponent(url.split('=')[1]).toUpperCase();
      return json(flights.filter((f) => f.callsign.includes(term)));
    }
    if (url === '/api/flights') return json(flights);
    if (url === '/api/health') return json({ status: 'ok', dataSource: 'fixtures (CAAS unreachable)' });
    if (url === '/api/airways') return json(['A464', 'G334']);
    if (url === '/api/airways/A464')
      return json({ name: 'A464', fixes: ['VJR', 'PIBOS', 'ATMAX'], points: [point('VJR'), point('PIBOS'), point('ATMAX')] });
    if (url === '/api/flights/f1/route')
      return json({ flight: flights[0], points: [point('WSSS', 'airport'), point('VJR', 'waypoint', 'A464'), point('WMKK', 'airport')], unresolved: ['ZZZ'] });
    if (url === '/api/flights/f1/alternate-route')
      return json({ flight: flights[0], points: [point('WSSS', 'airport'), point('BIKTA'), point('WMKK', 'airport')], unresolved: [] });
    if (url === '/api/flights/f2/alternate-route') return json({ message: 'No alternate route found' }, 404);
    if (url === '/api/flights/f2/route') return json({ flight: flights[1], points: [], unresolved: [] });
    throw new Error(`unexpected ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('App', () => {
  beforeEach(() => {
    mockFetch();
  });
  afterEach(() => vi.unstubAllGlobals());

  it('lists flights', async () => {
    render(<App />);
    expect(await screen.findByText('SIA200')).toBeInTheDocument();
    expect(screen.getByText('MAS604')).toBeInTheDocument();
  });

  it('lists, filters and shows airways', async () => {
    render(<App />);
    await userEvent.click(await screen.findByRole('tab', { name: /Airways \(2\)/ }));
    await userEvent.type(screen.getByLabelText('Filter airways'), 'a4');
    expect(screen.queryByText('G334')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('A464'));
    expect(await screen.findByText('VJR PIBOS ATMAX')).toBeInTheDocument();
    expect(screen.getByTestId('map')).toHaveTextContent('airway:VJR,PIBOS,ATMAX');
  });

  it('flags when sample data is shown instead of CAAS data', async () => {
    render(<App />);
    expect(await screen.findByText('Sample data: fixtures (CAAS unreachable)')).toBeInTheDocument();
  });

  it('filters flights by callsign', async () => {
    render(<App />);
    await screen.findByText('MAS604');
    await userEvent.type(screen.getByLabelText('Search by callsign'), 'sia');
    await waitFor(() => expect(screen.queryByText('MAS604')).not.toBeInTheDocument());
    expect(screen.getByText('SIA200')).toBeInTheDocument();
  });

  it('shows the selected route and toggles the alternate route', async () => {
    render(<App />);
    await userEvent.click(await screen.findByText('SIA200'));
    expect(await screen.findByText('WSSS VJR A464 WMKK')).toBeInTheDocument();
    expect(screen.getByText('Could not locate: ZZZ')).toBeInTheDocument();
    expect(screen.getByTestId('map')).toHaveTextContent('route:WSSS,VJR,WMKK');

    await userEvent.click(screen.getByRole('button', { name: 'Show alternate route' }));
    expect(await screen.findByText('Alternate: WSSS BIKTA WMKK')).toBeInTheDocument();
    expect(screen.getByTestId('map')).toHaveTextContent('alt:WSSS,BIKTA,WMKK');

    await userEvent.click(screen.getByRole('button', { name: 'Hide alternate route' }));
    expect(screen.queryByText(/Alternate:/)).not.toBeInTheDocument();
  });

  it('shows an error when no alternate route exists', async () => {
    render(<App />);
    await userEvent.click(await screen.findByText('MAS604'));
    await userEvent.click(await screen.findByRole('button', { name: 'Show alternate route' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No alternate route found');
  });
});
