package com.flightplan.caas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Subset of the CAAS Flight Object Model (flight-object-manager 1.0.0) that this app reads.
 * Every field is optional because real flight objects are sparsely populated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightObject(
        String _id,
        String gufi,
        String aircraftIdentification,
        String aircraftOperating,
        Departure departure,
        Arrival arrival,
        Aircraft aircraft,
        FiledRoute filedRoute) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Departure(JsonNode departureAerodrome, String dateOfFlight, String estimatedOffBLockTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Arrival(JsonNode destinationAerodrome) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aircraft(JsonNode aircraftType, String aircraftRegistration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FiledRoute(List<RouteElement> routeElement) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteElement(Integer seqNum, Position position, String airway, String airwayType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(Double lat, Double lon, String designatedPoint) {}

    public String departureCode() {
        return departure == null ? null : code(departure.departureAerodrome(), "locationIndicator");
    }

    public String destinationCode() {
        return arrival == null ? null : code(arrival.destinationAerodrome(), "locationIndicator");
    }

    public String aircraftTypeCode() {
        return aircraft == null ? null : code(aircraft.aircraftType(), "icaoAircraftTypeDesignator");
    }

    public List<RouteElement> routeElements() {
        return filedRoute == null || filedRoute.routeElement() == null ? List.of() : filedRoute.routeElement();
    }

    /** Codes appear either as a bare string or wrapped in an object, e.g. {"locationIndicator": "WSSS"}. */
    private static String code(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        JsonNode inner = node.get(field);
        return inner != null && inner.isTextual() ? inner.asText() : null;
    }
}
