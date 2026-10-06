# Flight Plan Viewer

This is the first iteration of the CAAS tech challenge. It lists the flight plans from the CAAS SWIM APIs, lets you search them by callsign, and draws the selected flight's filed route (waypoints and airways) on a world map. An **alternate route** button proposes a different route between the same two airports.

| | |
|---|---|
| **Backend** | Java 21 with Spring Boot 3 (Maven). Calls the CAAS APIs, keeps the API key on the server, caches data and resolves routes to coordinates |
| **Frontend** | React with Vite. Leaflet and OpenStreetMap draw the map |
| **Tests** | JUnit 5, AssertJ and MockMvc for the backend (JaCoCo coverage), Vitest and Testing Library for the frontend, Playwright end to end |
| **Delivery** | Docker multi-stage images, GitHub Actions, Artifact Registry and Google Cloud Run |

## Components

```mermaid
flowchart LR
  user([Browser]) -->|HTTPS| fe

  subgraph run[Google Cloud Run]
    fe["frontend<br/>nginx: static React bundle<br/>proxies /api"] -->|/api/*| be["backend<br/>Spring Boot API"]
  end

  be -->|"apikey header<br/>(from Secret Manager)"| caas[(CAAS SWIM APIs<br/>flight-manager/displayAll<br/>geopoints/list/airways<br/>geopoints/list/fixes)]
  user -.->|map tiles| osm[(OpenStreetMap)]

  subgraph ci[GitHub Actions]
    t[lint + test] --> e2e[docker compose + Playwright] --> push[build + push images] --> deploy[deploy to Cloud Run]
  end
  push --> ar[(Artifact Registry)]
  ar --> run
```

The browser only ever talks to the frontend container. nginx forwards `/api/*` to the backend, so the app needs no CORS setup, the backend URL is set at deploy time, and the CAAS key never reaches the browser.

### Backend (`backend/`)

| Endpoint | What it does |
|---|---|
| `GET /api/flights?callsign=SIA` | Lists flight plans sorted by callsign. Search is a case-insensitive substring match |
| `GET /api/flights/:id/route` | Returns the filed route resolved to coordinates, plus any designators that couldn't be located |
| `GET /api/flights/:id/alternate-route` | Returns an alternate route that avoids every filed waypoint (404 if none exists) |
| `GET /api/airways` | Lists all airways (air routes) with their points |
| `GET /api/health` | Health check that also says where the data comes from: `caas`, `fixtures`, or `fixtures (CAAS unreachable)` |
| `GET /api/docs` | Swagger UI |

Key pieces:

All code is under `src/main/java/com/flightplan/`.

- `caas/CaasClient.java` calls the CAAS APIs with Spring's `RestClient` and the `apikey` header (5 s timeout). It keeps an in-memory TTL cache: 60 s for flights and 6 h for airways and fixes, which are large and rarely change. **If CAAS is unreachable, or no `CAAS_API_KEY` is set, it serves the bundled `resources/fixtures/`** and retries CAAS every 5 minutes. The UI shows a "Sample data" badge whenever fixtures are being served. Settings are bound from `application.yml` into the `CaasProperties` record.
- `caas/FlightObject.java` holds Java records for the parts of the Flight Object Model that the app reads.
- `geo/GeoPoint.java` parses the aeronautical data format `"WSSL (1.42,103.87)"`. `geo/LatLon.java` computes great-circle distances.
- `route/RouteResolver.java` builds the route in order: departure aerodrome, the `filedRoute.routeElement`s sorted by `seqNum`, then the destination. Coordinates in the flight plan are used when present; otherwise the designator is looked up in fixes and airports. When an element continues on an airway, the airway's intermediate points are inserted, so the drawn line follows the airway instead of cutting straight across.
- `route/RouteGraph.java` builds a graph from airway segments and every filed route's segments, then runs **A\*** (great-circle heuristic) from departure to destination while avoiding the filed route's waypoints.

### Frontend (`frontend/`)

The sidebar has a **Flights** tab (callsign search and flight list) and an **Airways** tab, which lists all air routes and highlights one on the map when you click it. Selecting a flight draws its route in blue and shows the ICAO-style route string, for example `WSSS VJR A464 ATMAX WMKK`. **Show alternate route** draws the alternative as a dashed orange line.

## Run it locally

You need Docker, or Java 21 with Maven and Node 22 for the development servers.

```bash
# Whole stack in containers at http://localhost:8080 (fixture data unless a key is given)
CAAS_API_KEY=xxxxx docker compose up --build

# Or development servers with hot reload
cd backend  && CAAS_API_KEY=xxxxx mvn spring-boot:run   # http://localhost:8080/api/docs
cd frontend && npm ci && npm run dev                    # http://localhost:5173 (proxies /api to :8080)
```

## Test

```bash
cd backend  && mvn verify                  # unit tests + MockMvc API tests against fixtures; coverage in target/site/jacoco
cd frontend && npm run lint && npm test    # component tests (fetch mocked)
docker compose up -d --build && cd frontend && npx playwright test   # browser e2e against the containers
```

## CI/CD (`.github/workflows/pipeline.yml`)

1. **Backend** (`mvn verify`: compile, tests, coverage) and **Frontend** (`npm ci`, lint, typecheck, unit tests and build) run in parallel on every push and PR.
2. The **End-to-end** job builds both images with `docker compose`, starts them and runs Playwright against them.
3. **Deploy** runs only on `main`, after everything above passes. It authenticates to Google Cloud with Workload Identity Federation (keyless, so no JSON key is stored in GitHub). It then builds both images, tags them with the commit SHA and pushes them to Artifact Registry. Finally it deploys the backend to Cloud Run (the CAAS key comes from Secret Manager), deploys the frontend pointed at the backend's URL, and smoke-tests `/api/health`.

### CAAS API key

Add the key as a GitHub Actions secret named `CAAS_API_KEY` (Settings > Secrets and variables > Actions). Run the **CAAS API probe** workflow to check it works. The backend reads `CAAS_BASE_URL` (default `http://api.swimapisg.info:9080`), `CAAS_FLIGHTS_PATH` (default `/flightmanager/displayAll`) and `CAAS_GEO_PATH` (default `/geopoints/list`).

### One-time Google Cloud setup

```bash
PROJECT_ID=<gcp-project> GITHUB_REPO=SajeevU/flight-plan CAAS_API_KEY=<key> ./infra/setup-gcp.sh
```

The script enables the APIs and creates the Artifact Registry repo, the `caas-api-key` secret, the deployer service account and the Workload Identity pool. It then prints four values (`GCP_PROJECT_ID`, `GCP_REGION`, `GCP_SERVICE_ACCOUNT`, `GCP_WIF_PROVIDER`) to add as GitHub **repository variables**. Until those are set, the deploy job is skipped and CI still runs.

## Assumptions and known gaps

- The Swagger specs describe `routeElement.position` as `{lat, lon, designatedPoint}` and the geopoint lists as `"NAME (lat,lon)"` strings. The airways list is assumed to repeat the airway name once per point, in order, and airway expansion is skipped when that doesn't hold. Check these against the live API once the key is in place.
- The fixtures in `backend/src/main/resources/fixtures/` are illustrative (made-up coordinates). Regenerate them with `python3 backend/scripts/generate_fixtures.py`.
- As of October 2026 the CAAS API isn't reachable: port 9080 times out from GitHub, Cloud Run and a home network, and the HTTPS endpoint's certificate has expired. Until that's fixed, the deployed app serves fixtures.
- The alternate route is only as good as the known network: airways plus routes other flights have filed.

## Taking it to production

- Make the backend internal-only on Cloud Run and use service-to-service auth from the frontend. Add rate limiting.
- Move the cache to Redis or Memorystore so instances share it, and refresh airways and fixes on a schedule instead of on demand.
- Add structured logging, tracing and alerting with Cloud Logging and Monitoring.
- Infrastructure as code with Terraform instead of the bootstrap script. Add a staging environment with promotion to production.
- Add contract tests against the CAAS OpenAPI specs, and generate typed clients from them.
- Add dependency and image scanning (Dependabot, Trivy) to the pipeline.
- Use a better alternate-route model: airway direction and level restrictions, and avoiding airspace rather than just waypoints.
