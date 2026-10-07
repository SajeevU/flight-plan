package com.flightplan.airways;

import com.flightplan.route.Airway;
import com.flightplan.route.RoutePoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "airways")
@RestController
@RequestMapping("/api/airways")
public class AirwaysController {

    public record AirwayDetail(String name, List<String> fixes, List<RoutePoint> points) {}

    private final AeroData aero;

    public AirwaysController(AeroData aero) {
        this.aero = aero;
    }

    @Operation(summary = "Names of all airways (air routes), sorted; optionally filtered by a case-insensitive substring")
    @GetMapping
    public List<String> list(@Parameter(description = "Substring of the airway name") @RequestParam(required = false) String q) {
        String term = q == null ? "" : q.trim().toUpperCase(Locale.ROOT);
        return aero.airwayNames().stream().filter(n -> n.contains(term)).distinct().sorted().toList();
    }

    @Operation(summary = "One airway: its fixes in order and their coordinates")
    @GetMapping("/{name}")
    public AirwayDetail get(@PathVariable String name) {
        Airway airway = aero.airway(name.toUpperCase(Locale.ROOT));
        if (airway == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Airway " + name + " not found");
        return new AirwayDetail(airway.name(), airway.fixes(), airway.path(aero.geoIndex()));
    }
}
