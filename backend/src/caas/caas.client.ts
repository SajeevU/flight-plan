import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { FlightObject } from './flight-object';

export type GeoDataset = 'airways' | 'fixes' | 'airports';

interface CacheEntry {
  expires: number;
  value: Promise<unknown>;
}

/**
 * Thin client for the CAAS SWIM APIs. Keeps the API key server-side and caches
 * responses, since the airway and fix datasets are large and change rarely.
 *
 * With no CAAS_API_KEY (or CAAS_MOCK=true) it serves the bundled fixtures so the
 * app can be developed, tested and demoed without network access to CAAS.
 */
@Injectable()
export class CaasClient {
  private readonly logger = new Logger(CaasClient.name);
  private readonly baseUrl = process.env.CAAS_BASE_URL ?? 'https://api.swimapisg.info';
  private readonly apiKey = process.env.CAAS_API_KEY;
  readonly mock = process.env.CAAS_MOCK === 'true' || !this.apiKey;
  private readonly flightsTtlMs = Number(process.env.FLIGHTS_CACHE_SECONDS ?? 60) * 1000;
  private readonly geoTtlMs = Number(process.env.GEO_CACHE_SECONDS ?? 6 * 3600) * 1000;
  private readonly cache = new Map<string, CacheEntry>();

  constructor() {
    if (this.mock) this.logger.warn('CAAS_API_KEY not set or CAAS_MOCK=true: serving bundled fixture data');
  }

  listFlights(): Promise<FlightObject[]> {
    return this.cached('flights', this.flightsTtlMs, async () => {
      const data = await this.get('/flight-manager/displayAll', 'flights.json');
      return Array.isArray(data) ? (data as FlightObject[]) : [];
    });
  }

  listGeo(type: GeoDataset): Promise<string[]> {
    return this.cached(`geo:${type}`, this.geoTtlMs, async () => {
      const data = await this.get(`/geopoints/list/${type}`, `${type}.json`);
      return Array.isArray(data) ? (data as string[]) : [];
    });
  }

  private async get(path: string, fixture: string): Promise<unknown> {
    if (this.mock) {
      const raw = await readFile(join(__dirname, '..', '..', 'fixtures', fixture), 'utf8');
      return JSON.parse(raw);
    }
    const res = await fetch(`${this.baseUrl}${path}`, {
      headers: { apikey: this.apiKey as string, accept: 'application/json' },
      signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) {
      this.logger.error(`CAAS ${path} returned ${res.status}`);
      throw new ServiceUnavailableException(`Upstream CAAS API returned ${res.status}`);
    }
    return res.json();
  }

  private cached<T>(key: string, ttlMs: number, load: () => Promise<T>): Promise<T> {
    const hit = this.cache.get(key);
    if (hit && hit.expires > Date.now()) return hit.value as Promise<T>;
    const value = load();
    this.cache.set(key, { expires: Date.now() + ttlMs, value });
    // Don't cache failures: the next request retries upstream.
    value.catch(() => this.cache.delete(key));
    return value;
  }
}
