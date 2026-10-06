package com.flightplan.flights;

import com.flightplan.flights.FlightsService.FlightRoute;
import com.flightplan.flights.FlightsService.FlightSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "flights")
@RestController
@RequestMapping("/api/flights")
public class FlightsController {

    private final FlightsService flights;

    public FlightsController(FlightsService flights) {
        this.flights = flights;
    }

    @Operation(summary = "List flight plans, optionally filtered by callsign")
    @GetMapping
    public List<FlightSummary> list(
            @Parameter(description = "Case-insensitive substring of the callsign") @RequestParam(required = false) String callsign) {
        return flights.list(callsign);
    }

    @Operation(summary = "Filed route of a flight resolved to coordinates")
    @GetMapping("/{id}/route")
    public FlightRoute route(@PathVariable String id) {
        return flights.route(id);
    }

    @Operation(summary = "Alternate route between the same airports that avoids the filed waypoints")
    @GetMapping("/{id}/alternate-route")
    public FlightRoute alternateRoute(@PathVariable String id) {
        return flights.alternateRoute(id);
    }
}
