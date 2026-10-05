import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { configureApp } from '../src/app.setup';

// Runs the whole HTTP stack against the bundled fixtures (no CAAS key in tests).
describe('Flight Plan API (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    process.env.CAAS_MOCK = 'true';
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    configureApp(app);
    await app.init();
  });

  afterAll(() => app.close());

  it('GET /api/health reports the data source', async () => {
    const res = await request(app.getHttpServer()).get('/api/health').expect(200);
    expect(res.body).toMatchObject({ status: 'ok', dataSource: 'fixtures' });
  });

  it('GET /api/flights lists flights sorted by callsign', async () => {
    const res = await request(app.getHttpServer()).get('/api/flights').expect(200);
    expect(res.body.length).toBeGreaterThan(5);
    const callsigns = res.body.map((f: { callsign: string }) => f.callsign);
    expect(callsigns).toEqual([...callsigns].sort());
  });

  it('GET /api/flights?callsign= searches case-insensitively', async () => {
    const res = await request(app.getHttpServer()).get('/api/flights?callsign=sia').expect(200);
    expect(res.body.map((f: { callsign: string }) => f.callsign)).toEqual(['SIA200', 'SIA622', 'SIA978']);
  });

  it('GET /api/flights/:id/route resolves the route including airway points', async () => {
    const [flight] = (await request(app.getHttpServer()).get('/api/flights?callsign=SIA200')).body;
    const res = await request(app.getHttpServer()).get(`/api/flights/${flight.id}/route`).expect(200);
    expect(res.body.points.map((p: { name: string }) => p.name)).toEqual(['WSSS', 'VJR', 'A464', 'ATMAX', 'WMKK']);
    expect(res.body.unresolved).toEqual([]);
  });

  it('GET /api/flights/:id/alternate-route avoids the filed waypoints', async () => {
    const [flight] = (await request(app.getHttpServer()).get('/api/flights?callsign=SIA200')).body;
    const res = await request(app.getHttpServer()).get(`/api/flights/${flight.id}/alternate-route`).expect(200);
    expect(res.body.points.map((p: { name: string }) => p.name)).toEqual(['WSSS', 'BIKTA', 'TODAM', 'WMKK']);
  });

  it('returns 404 for an unknown flight', () => {
    return request(app.getHttpServer()).get('/api/flights/nope/route').expect(404);
  });

  it('GET /api/airways groups points by airway', async () => {
    const res = await request(app.getHttpServer()).get('/api/airways').expect(200);
    const a464 = res.body.find((a: { name: string }) => a.name === 'A464');
    expect(a464.points).toHaveLength(5);
  });
});
