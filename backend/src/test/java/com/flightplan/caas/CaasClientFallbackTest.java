package com.flightplan.caas;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightplan.caas.CaasClient.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** CAAS is unreachable (nothing listens on port 9): the client must fall back to fixtures, not fail. */
@SpringBootTest(properties = {"caas.base-url=http://127.0.0.1:9", "caas.api-key=test", "caas.timeout=2s"})
class CaasClientFallbackTest {

    @Autowired
    CaasClient caas;

    @Test
    void servesFixturesAndSaysSoWhenCaasIsDown() {
        assertThat(caas.listFlights()).isNotEmpty();
        assertThat(caas.dataSource()).isEqualTo(DataSource.FIXTURES_CAAS_UNREACHABLE);
        assertThat(caas.listGeo(CaasClient.GeoDataset.FIXES)).isNotEmpty();
    }
}
