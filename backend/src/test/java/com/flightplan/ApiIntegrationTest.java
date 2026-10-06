package com.flightplan;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Runs the whole HTTP stack against the bundled fixtures (no CAAS key in tests). */
@SpringBootTest(properties = "caas.mock=true")
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String idOf(String callsign) throws Exception {
        String body = mvc.perform(get("/api/flights").param("callsign", callsign)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    @Test
    void healthReportsTheDataSource() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.dataSource").value("fixtures"));
    }

    @Test
    void listsFlightsSortedByCallsign() throws Exception {
        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(5)))
                .andExpect(jsonPath("$[0].callsign").value("CPA712"));
    }

    @Test
    void searchesByCallsignCaseInsensitively() throws Exception {
        mvc.perform(get("/api/flights").param("callsign", "sia"))
                .andExpect(jsonPath("$[*].callsign", contains("SIA200", "SIA622", "SIA978")));
    }

    @Test
    void resolvesARouteIncludingAirwayPoints() throws Exception {
        mvc.perform(get("/api/flights/{id}/route", idOf("SIA200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[*].name", contains("WSSS", "VJR", "A464", "ATMAX", "WMKK")))
                .andExpect(jsonPath("$.points[1].kind").value("waypoint"))
                .andExpect(jsonPath("$.points[0].airway").doesNotExist())
                .andExpect(jsonPath("$.unresolved", hasSize(0)));
    }

    @Test
    void alternateRouteAvoidsTheFiledWaypoints() throws Exception {
        mvc.perform(get("/api/flights/{id}/alternate-route", idOf("SIA200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[*].name", contains("WSSS", "BIKTA", "TODAM", "WMKK")));
    }

    @Test
    void unknownFlightIs404WithAMessage() throws Exception {
        mvc.perform(get("/api/flights/nope/route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Flight nope not found"));
    }

    @Test
    void groupsAirwayPointsByAirway() throws Exception {
        mvc.perform(get("/api/airways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'A464')].points[*]", hasSize(5)));
    }
}
