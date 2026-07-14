package com.archivenexus.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_nexus_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "archive-nexus.simulator.persistence-enabled=false",
        "archive.integrations.logitics.enabled=false",
        "archive.integrations.ledger.enabled=false",
        "archive.security.enabled=true",
        "archive.tokens.nexus-to-logistics=logistics-token",
        "archive.tokens.nexus-to-ledger=ledger-token",
        "archive.tokens.nexus-to-os=archiveos-token",
        "archive.security.market-token=market-token",
        "archive.security.operator-token=operator-token",
        "archive.security.reader-token=reader-token"
})
@AutoConfigureMockMvc
class ArchiveServiceAuthenticationFilterTest {
    @Autowired MockMvc mvc;

    @Test
    void missingTokenIsRejectedForWrite() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=1")
                        .header("Authorization", "Bearer invalid")
                        .header("X-Archive-Source-System", "archive-os")
                        .header("X-Archive-Service-Scope", "admin:operate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedSourceOrScopeIsForbidden() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=1")
                        .header("Authorization", "Bearer operator-token")
                        .header("X-Archive-Source-System", "archive-market")
                        .header("X-Archive-Service-Scope", "production:ingest"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validAdminIdentityCanUseAdminWrite() throws Exception {
        mvc.perform(post("/api/outbox/events/generate?count=1")
                        .header("Authorization", "Bearer operator-token")
                        .header("X-Archive-Source-System", "archive-os")
                        .header("X-Archive-Service-Scope", "admin:operate"))
                .andExpect(status().isOk());
    }

    @Test
    void sensitiveReadRequiresReaderIdentity() throws Exception {
        mvc.perform(get("/api/outbox/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/outbox/events")
                        .header("Authorization", "Bearer reader-token")
                        .header("X-Archive-Source-System", "archive-os")
                        .header("X-Archive-Service-Scope", "authenticated:read"))
                .andExpect(status().isOk());
    }

    @Test
    void publicSummaryRemainsReadable() throws Exception {
        mvc.perform(get("/api/operations/summary")).andExpect(status().isOk());
    }
}
