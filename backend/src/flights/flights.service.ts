import { Injectable, NotFoundException } from '@nestjs/common';
import { CaasClient } from '../caas/caas.client';
import { FlightObject, aerodromeCode } from '../caas/flight-object';
import { parseGeoPoints } from '../geo/geopoint';
import { RouteGraph, findAlternateRoute } from './alternate-route';
import { GeoIndex, ResolvedRoute, RoutePoint, buildGeoIndex, resolveRoute } from './route.resolver';

export interface FlightSummary {
  id: string;
  callsign: string;
  departure?: string;
  destination?: string;
  aircraftType?: string;
  dateOfFlight?: string;
  estimatedOffBlockTime?: string;
}

export interface FlightRoute extends ResolvedRoute {
  flight: FlightSummary;
}

@Injectable()
export class FlightsService {
  constructor(private readonly caas: CaasClient) {}

  async list(callsign?: string): Promise<FlightSummary[]> {
    const term = callsign?.trim().toUpperCase();
    const flights = (await this.caas.listFlights()).map(toSummary);
    const matches = term ? flights.filter((f) => f.callsign.toUpperCase().includes(term)) : flights;
    return matches.sort((a, b) => a.callsign.localeCompare(b.callsign));
  }

  async route(id: string): Promise<FlightRoute> {
    const flight = await this.find(id);
    const route = resolveRoute(flight, await this.geoIndex());
    return { flight: toSummary(flight), ...route };
  }

  async alternateRoute(id: string): Promise<FlightRoute> {
    const flight = await this.find(id);
    const geo = await this.geoIndex();
    const filed = resolveRoute(flight, geo);
    const others = (await this.caas.listFlights()).map((f) => resolveRoute(f, geo).points);
    const alternate = findAlternateRoute(filed.points, RouteGraph.build(geo, others));
    if (!alternate) throw new NotFoundException('No alternate route found in the known airway network');
    return { flight: toSummary(flight), points: alternate, unresolved: [] };
  }

  private async find(id: string): Promise<FlightObject> {
    const flights = await this.caas.listFlights();
    const flight = flights.find((f) => flightId(f) === id);
    if (!flight) throw new NotFoundException(`Flight ${id} not found`);
    return flight;
  }

  private async geoIndex(): Promise<GeoIndex> {
    const [fixes, airports, airways] = await Promise.all([
      this.caas.listGeo('fixes'),
      // Airports are optional: older datasets fold them into fixes.
      this.caas.listGeo('airports').catch(() => []),
      this.caas.listGeo('airways'),
    ]);
    return buildGeoIndex(parseGeoPoints(fixes), parseGeoPoints(airports), parseGeoPoints(airways));
  }
}

export function flightId(f: FlightObject): string {
  return f.gufi ?? f._id ?? `${f.aircraftIdentification}-${f.departure?.dateOfFlight ?? ''}`;
}

export function toSummary(f: FlightObject): FlightSummary {
  const type = f.aircraft?.aircraftType;
  return {
    id: flightId(f),
    callsign: f.aircraftIdentification ?? 'UNKNOWN',
    departure: aerodromeCode(f.departure?.departureAerodrome),
    destination: aerodromeCode(f.arrival?.destinationAerodrome),
    aircraftType: typeof type === 'string' ? type : type?.icaoAircraftTypeDesignator,
    dateOfFlight: f.departure?.dateOfFlight,
    estimatedOffBlockTime: f.departure?.estimatedOffBLockTime,
  };
}

export type { RoutePoint };
