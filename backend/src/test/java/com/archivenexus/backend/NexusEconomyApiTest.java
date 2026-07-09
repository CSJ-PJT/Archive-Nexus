package com.archivenexus.backend;

import com.archivenexus.backend.nexuseconomy.NexusCostEventRepository;
import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.CostType;
import com.archivenexus.backend.nexuseconomy.NexusEconomyModels.RevenueType;
import com.archivenexus.backend.nexuseconomy.NexusProfitSnapshotRepository;
import com.archivenexus.backend.nexuseconomy.NexusRevenueEventRepository;
import com.archivenexus.backend.persistence.DomainAggregateProjectionService;
import com.archivenexus.backend.service.NexusStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_economy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class NexusEconomyApiTest {
    @Autowired MockMvc mvc;
    @Autowired NexusStateService state;
    @Autowired NexusRevenueEventRepository revenues;
    @Autowired NexusCostEventRepository costs;
    @Autowired NexusProfitSnapshotRepository snapshots;
    @MockBean DomainAggregateProjectionService projections;

    @BeforeEach
    void cleanEconomyTables() {
        snapshots.deleteAll();
        costs.deleteAll();
        revenues.deleteAll();
    }

    @Test
    void productionTickCreatesRevenueAndMaterialCostEvents() throws Exception {
        state.generateTick();

        assertThat(revenues.countByRevenueType(RevenueType.PRODUCTION_REVENUE_RECOGNIZED)).isGreaterThan(0);
        assertThat(costs.countByCostType(CostType.MATERIAL_COST_INCURRED)).isGreaterThan(0);
        assertThat(costs.countByCostType(CostType.NEXUS_OPERATION_COST_INCURRED)).isGreaterThan(0);

        mvc.perform(get("/api/nexus-economy/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").isNumber())
                .andExpect(jsonPath("$.totalCost").isNumber())
                .andExpect(jsonPath("$.profit").isNumber())
                .andExpect(jsonPath("$.cashBalance").isNumber())
                .andExpect(jsonPath("$.bankruptcyRisk").exists())
                .andExpect(jsonPath("$.revenueByType.PRODUCTION_REVENUE_RECOGNIZED").isNumber())
                .andExpect(jsonPath("$.costByType.MATERIAL_COST_INCURRED").isNumber());
    }

    @Test
    void externalLogisticsFeeCreatesCostEventAndDuplicateDoesNotCreateAgain() throws Exception {
        String body = """
                {
                  "eventId": "LOG-FEE-001",
                  "idempotencyKey": "Archive-Logistics:LOGISTICS_SERVICE_FEE_PAID:LOG-FEE-001",
                  "simulationRunId": "SIM-DEMO",
                  "settlementCycleId": "CYCLE-001",
                  "correlationId": "corr-log-001",
                  "causationId": "route-cost-001",
                  "hopCount": 1,
                  "maxHop": 8,
                  "sourceService": "Archive-Logistics",
                  "costType": "LOGISTICS_SERVICE_FEE_PAID",
                  "costAmount": 125000,
                  "currency": "KRW",
                  "reason": "Synthetic logistics service fee billed to Nexus"
                }
                """;

        mvc.perform(post("/api/economy/events/external").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.type").value("LOGISTICS_SERVICE_FEE_PAID"))
                .andExpect(jsonPath("$.sourceService").value("Archive-Logistics"));

        mvc.perform(post("/api/economy/events/external").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(costs.count()).isEqualTo(1);
        assertThat(costs.sumCostByType(CostType.LOGISTICS_SERVICE_FEE_PAID))
                .isEqualByComparingTo(new BigDecimal("125000.00"));
    }

    @Test
    void externalLedgerFeeCreatesCostEvent() throws Exception {
        mvc.perform(post("/api/economy/events/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "LEDGER-FEE-001",
                                  "idempotencyKey": "Archive-Ledger:LEDGER_SETTLEMENT_AGENCY_FEE_PAID:LEDGER-FEE-001",
                                  "simulationRunId": "SIM-DEMO",
                                  "settlementCycleId": "CYCLE-001",
                                  "correlationId": "corr-ledger-001",
                                  "causationId": "settlement-001",
                                  "hopCount": 1,
                                  "maxHop": 8,
                                  "sourceService": "Archive-Ledger",
                                  "costType": "LEDGER_SETTLEMENT_AGENCY_FEE_PAID",
                                  "costAmount": 33000,
                                  "currency": "KRW",
                                  "reason": "Synthetic ledger settlement agency fee billed to Nexus"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.type").value("LEDGER_SETTLEMENT_AGENCY_FEE_PAID"))
                .andExpect(jsonPath("$.sourceService").value("Archive-Ledger"));

        assertThat(costs.countByCostType(CostType.LEDGER_SETTLEMENT_AGENCY_FEE_PAID)).isEqualTo(1);
    }

    @Test
    void hopCountAboveMaxHopIsRejected() throws Exception {
        mvc.perform(post("/api/economy/events/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "LOG-FEE-HOP-REJECTED",
                                  "idempotencyKey": "Archive-Logistics:LOGISTICS_SERVICE_FEE_PAID:HOP-REJECTED",
                                  "sourceService": "Archive-Logistics",
                                  "costType": "LOGISTICS_SERVICE_FEE_PAID",
                                  "costAmount": 1000,
                                  "currency": "KRW",
                                  "hopCount": 9,
                                  "maxHop": 3,
                                  "reason": "Synthetic recursive fee guard test"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(costs.count()).isZero();
    }

    @Test
    void dailyCloseCreatesProfitSnapshot() throws Exception {
        state.generateTick();

        mvc.perform(post("/api/nexus-economy/daily-close?date=2026-07-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").exists())
                .andExpect(jsonPath("$.settlementDate").value("2026-07-09"))
                .andExpect(jsonPath("$.revenueAmount").isNumber())
                .andExpect(jsonPath("$.costAmount").isNumber())
                .andExpect(jsonPath("$.profitAmount").isNumber())
                .andExpect(jsonPath("$.cashBalance").isNumber())
                .andExpect(jsonPath("$.bankruptcyRisk").exists());

        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void integrationSummaryExposesEconomySummaryPath() throws Exception {
        mvc.perform(get("/api/integrations/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.economySummaryPath").value("/api/nexus-economy/summary"));
    }
}
