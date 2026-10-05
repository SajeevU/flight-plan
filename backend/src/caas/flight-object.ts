// Subset of the CAAS Flight Object Model (flight-object-manager 1.0.0) that this app reads.
// Fields are optional because real flight objects are sparsely populated.

export interface RoutePosition {
  lat?: number;
  lon?: number;
  designatedPoint?: string;
}

export interface RouteElement {
  seqNum?: number;
  position?: RoutePosition;
  airway?: string;
  airwayType?: string;
}

export interface FlightObject {
  _id?: string;
  gufi?: string;
  aircraftIdentification?: string;
  aircraftOperating?: string;
  flightType?: string;
  lastUpdatedTimeStamp?: string;
  departure?: {
    departureAerodrome?: string | { locationIndicator?: string };
    dateOfFlight?: string;
    estimatedOffBLockTime?: string;
  };
  arrival?: {
    destinationAerodrome?: string | { locationIndicator?: string };
  };
  aircraft?: {
    aircraftType?: string | { icaoAircraftTypeDesignator?: string };
    aircraftRegistration?: string;
  };
  filedRoute?: {
    routeElement?: RouteElement[];
  };
}

/** Aerodromes appear either as a bare ICAO code or wrapped in an object. */
export function aerodromeCode(value: string | { locationIndicator?: string } | undefined): string | undefined {
  if (!value) return undefined;
  return typeof value === 'string' ? value : value.locationIndicator;
}
