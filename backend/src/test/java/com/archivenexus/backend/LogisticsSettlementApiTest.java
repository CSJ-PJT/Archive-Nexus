package com.archivenexus.backend;

import com.archivenexus.backend.logisticssettlement.LogisticsDailySettlementRepository;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_logistics_settlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=false",
        "archive.integrations.ledger.enabled=false"
})
@AutoConfigureMockMvc
class LogisticsSettlementApiTest {
    @Autowired MockMvc mvc;
    @Autowired LogisticsDailySettlementRepository repository;
    @MockBean DomainAggregateProjectionService projections;

    @Test
    void receivesDailySettlementIdempotently() throws Exception {
        repository.deleteAll();
        String body = """
                {
                  "settlementId": "LGS-SETTLE-20260709-FAC-A",
                  "idempotencyKey": "LOGISTICS:DAILY:2026-07-09:FAC-A",
                  "source": "Archive-Logistics",
                  "schemaVersion": 1,
                  "settlementDate": "2026-07-09",
                  "factoryId": "FAC-A",
                  "currency": "KRW",
                  "totalShipments": 12,
                  "delayedShipments": 2,
                  "heldShipments": 1,
                  "totalQuantity": 1440,
                  "totalLogisticsCost": 3800000,
                  "manufacturingImpactCost": 720000,
                  "onTimeRate": 0.8333,
                  "evidence": {"basis": "synthetic daily route cost summary"},
                  "payload": {"demoData": true},
                  "occurredAt": "2026-07-09T10:00:00Z"
                }
                """;

        mvc.perform(post("/api/logistics/settlements/daily").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value("LGS-SETTLE-20260709-FAC-A"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.factoryId").value("FAC-A"))
                .andExpect(jsonPath("$.totalShipments").value(12));

        mvc.perform(post("/api/logistics/settlements/daily").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.duplicateCount").value(1));

        assertThat(repository.count()).isEqualTo(1);

        mvc.perform(get("/api/logistics/settlements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.received").value(1))
                .andExpect(jsonPath("$.factories[0].factoryId").value("FAC-A"));
    }

    @Test
    void rejectsInvalidSettlementWithoutChangingManufacturingRuntime() throws Exception {
        repository.deleteAll();
        mvc.perform(post("/api/logistics/settlements/daily").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
        mvc.perform(get("/api/simulator/status")).andExpect(status().isOk());
    }
}
