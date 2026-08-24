package com.archivenexus.backend.runtime;

import com.archivenexus.backend.outbox.OutboxEventService;
import com.archivenexus.backend.outbox.OutboxEventService.EconomyAggregate;
import com.archivenexus.backend.outbox.OutboxModels.EventType;
import com.archivenexus.backend.outbox.OutboxModels.OutboxEventResponse;
import com.archivenexus.backend.outbox.OutboxModels.OutboxStatus;
import com.archivenexus.backend.outbox.OutboxModels.OutboxTargetService;
import com.archivenexus.backend.outbox.OutboxModels.RoutingStatus;
import com.archivenexus.backend.runtime.RuntimeEventModels.EconomyOperationsSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeEventServiceEconomyTest {
    private static final String SCOPE = "PERSISTED_OUTBOX_EVENTS_LAST_24_HOURS";

    @Test
    void calculatesRecognizedProfitForOneBoundedPersistedWindowWithoutInventingCash() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        Instant sourceLatestEventAt = Instant.parse("2026-08-20T01:02:03Z");
        when(outbox.economyAggregate(any(Instant.class), any(Instant.class))).thenReturn(new EconomyAggregate(
                5, 2, 1, 1,
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("25"), new BigDecimal("10"), SCOPE, sourceLatestEventAt));

        EconomyOperationsSummary summary = service(outbox).economySummary();

        assertThat(summary.manufacturingRevenue()).isEqualByComparingTo("1000");
        assertThat(summary.totalCost()).isEqualByComparingTo("185");
        assertThat(summary.operatingProfit()).isEqualByComparingTo("815");
        assertThat(summary.workforceCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.cashBalance()).isNull();
        assertThat(summary.status()).isEqualTo("SYNTHETIC_PERSISTED_OUTBOX_24H");
        assertThat(summary.calculationScope()).isEqualTo(SCOPE);
        assertThat(summary.currency()).isEqualTo("SYNTHETIC_KRW");
        assertThat(summary.sourceLatestEventAt()).isEqualTo(sourceLatestEventAt);
        assertThat(summary.periodEnd()).isEqualTo(summary.calculatedAt());
        assertThat(Duration.between(summary.periodStart(), summary.periodEnd())).isEqualTo(Duration.ofHours(24));

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).economyAggregate(start.capture(), end.capture());
        assertThat(Duration.between(start.getValue(), end.getValue())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void fallbackRecognizesNonTerminalPersistedEventsAndIgnoresTerminalOrOutOfWindowEvents() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        when(outbox.economyAggregate(any(Instant.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("native aggregate unavailable"));
        when(outbox.recognizedEventsBetween(any(Instant.class), any(Instant.class), eq(1000)))
                .thenAnswer(invocation -> {
                    Instant start = invocation.getArgument(0);
                    Instant end = invocation.getArgument(1);
                    return List.of(
                            event(1, EventType.PRODUCTION_COMPLETED, OutboxStatus.PUBLISHED,
                                    Map.of("totalAmount", 1000, "producedQuantity", 999), end.minus(Duration.ofHours(1))),
                            event(2, EventType.PRODUCTION_COMPLETED, OutboxStatus.PUBLISHED,
                                    Map.of("producedQuantity", 999), end.minus(Duration.ofHours(2))),
                            event(3, EventType.MATERIAL_CONSUMED, OutboxStatus.PUBLISHED,
                                    Map.of("amount", 100), end.minus(Duration.ofMinutes(30))),
                            event(7, EventType.MAINTENANCE_REQUIRED, OutboxStatus.PUBLISHED,
                                    Map.of(), end.minus(Duration.ofMinutes(5))),
                            event(4, EventType.PRODUCTION_COMPLETED, OutboxStatus.PENDING,
                                    Map.of("totalAmount", 5000), end.minus(Duration.ofMinutes(10))),
                            event(8, EventType.PRODUCTION_COMPLETED, OutboxStatus.FAILED,
                                    Map.of("totalAmount", 7000), end.minus(Duration.ofMinutes(8))),
                            event(5, EventType.PRODUCTION_COMPLETED, OutboxStatus.PUBLISHED,
                                    Map.of("totalAmount", 5000), start.minusSeconds(1)),
                            event(6, EventType.PRODUCTION_COMPLETED, OutboxStatus.PUBLISHED,
                                    Map.of("totalAmount", 5000), end.plusSeconds(1))
                    );
                });

        EconomyOperationsSummary summary = service(outbox).economySummary();

        assertThat(summary.manufacturingRevenue()).isEqualByComparingTo("6000");
        assertThat(summary.materialCost()).isEqualByComparingTo("100");
        assertThat(summary.totalCost()).isEqualByComparingTo("100");
        assertThat(summary.operatingProfit()).isEqualByComparingTo("5900");
        assertThat(summary.cashBalance()).isNull();
        assertThat(summary.calculationScope()).isEqualTo(SCOPE + "_FALLBACK_LATEST_1000");
        assertThat(summary.currency()).isEqualTo("SYNTHETIC_KRW");
        assertThat(summary.sourceLatestEventAt()).isEqualTo(summary.periodEnd().minus(Duration.ofMinutes(10)));
    }

    @Test
    void fallbackDoesNotReportFinanceAvailabilityForNonFinanceOrAmountlessProductionEvents() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        when(outbox.economyAggregate(any(Instant.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("native aggregate unavailable"));
        when(outbox.recognizedEventsBetween(any(Instant.class), any(Instant.class), eq(1000)))
                .thenAnswer(invocation -> {
                    Instant end = invocation.getArgument(1);
                    return List.of(
                            event(1, EventType.MAINTENANCE_REQUIRED, OutboxStatus.PUBLISHED,
                                    Map.of(), end.minus(Duration.ofMinutes(2))),
                            event(2, EventType.PRODUCTION_COMPLETED, OutboxStatus.PUBLISHED,
                                    Map.of("producedQuantity", 25), end.minus(Duration.ofMinutes(1))));
                });

        EconomyOperationsSummary summary = service(outbox).economySummary();

        assertThat(summary.available()).isTrue();
        assertThat(summary.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.sourceLatestEventAt()).isNull();
        assertThat(summary.financialEventCount()).isZero();
        assertThat(summary.querySucceeded()).isTrue();
    }

    @Test
    void returnsExplicitZeroActivityContractWhenNoPersistedFinanceEventsExistInTheWindow() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        when(outbox.economyAggregate(any(Instant.class), any(Instant.class))).thenReturn(new EconomyAggregate(
                0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                SCOPE, null));

        EconomyOperationsSummary summary = service(outbox).economySummary();

        assertThat(summary.available()).isTrue();
        assertThat(summary.status()).isEqualTo("NO_ACTIVITY");
        assertThat(summary.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.profit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.cashBalance()).isNull();
        assertThat(summary.calculationScope()).isEqualTo(SCOPE);
        assertThat(summary.currency()).isEqualTo("SYNTHETIC_KRW");
        assertThat(summary.sourceLatestEventAt()).isNull();
        assertThat(summary.financialEventCount()).isZero();
        assertThat(summary.querySucceeded()).isTrue();
        assertThat(Duration.between(summary.periodStart(), summary.periodEnd())).isEqualTo(Duration.ofHours(24));
    }

    private RuntimeEventService service(OutboxEventService outbox) {
        return new RuntimeEventService(outbox, null, null, null, null, null, new ObjectMapper());
    }

    private OutboxEventResponse event(long id, EventType type, OutboxStatus status,
                                      Map<String, Object> payload, Instant createdAt) {
        return new OutboxEventResponse(
                id, "event-" + id, "idempotency-" + id, type, "Factory", "FAC-A", "Archive-Nexus", 1,
                payload, status, 0, null, OutboxTargetService.LEDGER, null, RoutingStatus.ROUTED,
                OutboxTargetService.LEDGER, createdAt, null, createdAt, createdAt,
                status == OutboxStatus.PUBLISHED ? createdAt : null);
    }
}
