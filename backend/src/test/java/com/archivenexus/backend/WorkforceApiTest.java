package com.archivenexus.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_workforce;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.workforce.enabled=true",
        "archive.workforce.baseline-capacity=90"
})
@AutoConfigureMockMvc
class WorkforceApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void assignsWorkforceAndExposesSummaries() throws Exception {
        mvc.perform(post("/api/workforce/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "WF-EVT-001",
                                  "idempotencyKey": "WF-IDEMP-001",
                                  "sourceService": "ArchiveOS",
                                  "eventType": "WORKFORCE_ALLOCATION_ASSIGNED",
                                  "role": "PRODUCTION_OPERATOR",
                                  "allocatedHeadcount": 10,
                                  "capacityPerPersonPerDay": 20,
                                  "productivityScore": 1.2,
                                  "wagePerDay": 150000,
                                  "workdayId": "WD-001",
                                  "simulationRunId": "SIM-001",
                                  "settlementCycleId": "CYCLE-001",
                                  "correlationId": "CORR-001",
                                  "causationId": "CAUSE-001",
                                  "hopCount": 0,
                                  "maxHop": 8,
                                  "payload": {"synthetic": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.allocatedHeadcount").value(10))
                .andExpect(jsonPath("$.effectiveCapacity").value(240));

        mvc.perform(get("/api/workforce/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.activeProductionOperators").value(10))
                .andExpect(jsonPath("$.dailyLaborCostKrw").value(1500000));

        mvc.perform(get("/api/capacity/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionCapacity").isNumber())
                .andExpect(jsonPath("$.estimatedDailyCapacity").isNumber());

        mvc.perform(post("/api/workforce/workday/run").param("date", "2026-07-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workdayId").value("NEXUS-WORKDAY-2026-07-10"))
                .andExpect(jsonPath("$.status").isNotEmpty());

        mvc.perform(get("/api/productivity/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedWorkdays").value(1));
    }

    @Test
    void duplicateAllocationDoesNotCreateAnotherActiveAllocation() throws Exception {
        String body = """
                {
                  "eventId": "WF-EVT-DUP",
                  "idempotencyKey": "WF-IDEMP-DUP",
                  "sourceService": "Archive-Market",
                  "role": "QUALITY_INSPECTOR",
                  "allocatedHeadcount": 3,
                  "hopCount": 0,
                  "maxHop": 8
                }
                """;

        mvc.perform(post("/api/workforce/allocations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        mvc.perform(post("/api/workforce/allocations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void hopOverflowIsRejected() throws Exception {
        mvc.perform(post("/api/workforce/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "WF-EVT-HOP",
                                  "idempotencyKey": "WF-IDEMP-HOP",
                                  "sourceService": "ArchiveOS",
                                  "role": "MAINTENANCE_ENGINEER",
                                  "allocatedHeadcount": 5,
                                  "hopCount": 9,
                                  "maxHop": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("hopCount exceeds maxHop"));
    }
}
