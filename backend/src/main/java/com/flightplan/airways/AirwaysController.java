package com.flightplan.airways;

import com.flightplan.caas.CaasClient;
import com.flightplan.caas.CaasClient.GeoDataset;
import com.flightplan.geo.GeoPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "airways")
@RestController
@RequestMapping("/api/airways")
public class AirwaysController {

    public record Point(double lat, double lon) {}

    public record Airway(String name, List<Point> points) {}

    private final CaasClient caas;

    public AirwaysController(CaasClient caas) {
        this.caas = caas;
    }

    @Operation(summary = "All airways (air routes) with their points in published order")
    @GetMapping
    public List<Airway> list() {
        Map<String, List<Point>> byName = new LinkedHashMap<>();
        for (GeoPoint p : GeoPoint.parseAll(caas.listGeo(GeoDataset.AIRWAYS))) {
            byName.computeIfAbsent(p.name(), k -> new ArrayList<>()).add(new Point(p.lat(), p.lon()));
        }
        return byName.entrySet().stream()
                .map(e -> new Airway(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Airway::name))
                .toList();
    }
}
