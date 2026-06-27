package com.archivenexus.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.archivenexus.backend.service.NexusStateService;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
class NexusApiSmokeTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    NexusStateService nexus;

    @Test
    void exposesFactorySimulatorAndRpaApis() throws Exception {
        mvc.perform(get("/api/factories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(get("/api/simulator/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factoryCount").value(3))
                .andExpect(jsonPath("$.parallelWorkerCount").value(3));

        mvc.perform(post("/api/simulator/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        mvc.perform(get("/api/rpa/tasks"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/archiveos/interactions"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/simulator/persistence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void createsBatchSnapshotWithoutCallingArchiveOsForNormalAggregation() throws Exception {
        nexus.generateTick();
        nexus.generateTick();
        nexus.generateTick();
        nexus.generateTick();

        mvc.perform(get("/api/batch/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tick").value(5));

        mvc.perform(get("/api/archiveos/interactions"))
                .andExpect(status().isOk());
    }

    @Test
    void exposesPrometheusOperationalMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("archive_nexus_simulator_running")))
                .andExpect(content().string(containsString("archive_nexus_simulator_tick")))
                .andExpect(content().string(containsString("archive_nexus_factory_count")))
                .andExpect(content().string(containsString("archive_nexus_anomaly_count")))
                .andExpect(content().string(containsString("archive_nexus_rpa_task_count")))
                .andExpect(content().string(containsString("archive_nexus_batch_snapshot_count")))
                .andExpect(content().string(containsString("archive_nexus_persistence_save_total")))
                .andExpect(content().string(containsString("archive_nexus_restore_source_total")));
    }
}
