package com.flightplan.health;

import com.flightplan.caas.CaasClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "health")
@RestController
public class HealthController {

    private final CaasClient caas;
    private final String version;

    public HealthController(CaasClient caas, @Value("${app.version:dev}") String version) {
        this.caas = caas;
        this.version = version;
    }

    /** Liveness plus which data the API is currently serving: caas, fixtures, or fixtures because CAAS is unreachable. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        String source = switch (caas.dataSource()) {
            case CAAS -> "caas";
            case FIXTURES -> "fixtures";
            case FIXTURES_CAAS_UNREACHABLE -> caas.snapshotTakenAt() != null
                    ? "CAAS snapshot from " + caas.snapshotTakenAt() + " (live CAAS unreachable)"
                    : "fixtures (CAAS unreachable)";
        };
        var body = new java.util.LinkedHashMap<String, String>(Map.of("status", "ok", "dataSource", source, "version", version));
        if (caas.lastError() != null) body.put("lastCaasError", caas.lastError());
        return body;
    }
}
