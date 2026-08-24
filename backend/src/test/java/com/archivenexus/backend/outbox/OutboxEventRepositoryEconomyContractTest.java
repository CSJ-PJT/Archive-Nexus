package com.archivenexus.backend.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventRepositoryEconomyContractTest {

    @Test
    void recognizedEconomyQueryUsesOnlyNonTerminalPersistedFinanceEventsInsideTheRequestedWindow() throws Exception {
        Method method = OutboxEventRepository.class.getMethod("aggregateEconomy", Instant.class, Instant.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        String sql = query.value().replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("where status in ('PENDING', 'PUBLISHED', 'PENDING_RETRY')")
                .contains("created_at >= :since")
                .contains("created_at <= :until")
                .contains("count(*) filter (where event_type in ('MATERIAL_CONSUMED', 'MAINTENANCE_COMPLETED'")
                .contains("max(created_at) filter (where event_type in ('MATERIAL_CONSUMED', 'MAINTENANCE_COMPLETED'")
                .contains("as \"sourceLatestEventAt\"")
                .contains("event_type = 'PRODUCTION_COMPLETED' and nullif(trim(payload::jsonb ->> 'totalAmount'), '') is not null")
                .contains("payload::jsonb ->> 'totalAmount'")
                .doesNotContain("producedQuantity")
                .doesNotContain("productionCompleted")
                .doesNotContain("120000");
    }

    @Test
    void fallbackRepositoryMethodRequiresAnExplicitNonTerminalStatusSetAndBothWindowBounds() throws Exception {
        Method method = OutboxEventRepository.class.getMethod(
                "findAllByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc",
                java.util.Collection.class, Instant.class, Instant.class, Pageable.class);

        assertThat(method).isNotNull();
    }
}
