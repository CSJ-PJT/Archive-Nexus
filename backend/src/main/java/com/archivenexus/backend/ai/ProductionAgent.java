package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import com.archivenexus.backend.domain.DomainModels.ProductionOrder;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductionAgent implements ManufacturingAgent {
    private final NexusStateService nexus;

    public ProductionAgent(NexusStateService nexus) {
        this.nexus = nexus;
    }

    @Override
    public String getName() {
        return "ProductionAgent";
    }

    @Override
    public boolean supports(Intent intent) {
        return intent == Intent.PRODUCTION;
    }

    @Override
    public AgentResult analyze(AgentContext context) {
        long startedAt = System.nanoTime();
        List<ProductionOrder> orders = filter(nexus.productionOrders(), context.selectedFactoryId());
        if (orders.isEmpty()) {
            return insufficient(startedAt);
        }

        int target = orders.stream().mapToInt(ProductionOrder::targetQuantity).sum();
        int produced = orders.stream().mapToInt(ProductionOrder::producedQuantity).sum();
        double attainment = target == 0 ? 0 : (double) produced / target;
        ProductionOrder latest = orders.get(orders.size() - 1);
        double latestAttainment = latest.targetQuantity() == 0 ? 0 : (double) latest.producedQuantity() / latest.targetQuantity();
        double previousAttainment = orders.size() < 2 ? latestAttainment : attainment(orders.get(orders.size() - 2));
        double drop = Math.max(0, previousAttainment - latestAttainment);
        boolean actionRequired = latestAttainment < 0.82 || drop >= 0.15;

        List<AgentEvidence> evidence = List.of(
                evidence("production_attainment", "목표 대비 누적 생산 달성률", percent(attainment), "productionOrders"),
                evidence("latest_output", "최근 생산 실적", latest.producedQuantity() + "/" + latest.targetQuantity(), latest.id()),
                evidence("recent_drop", "직전 대비 생산 달성률 하락", percent(drop), "recent production orders")
        );
        List<String> actions = new ArrayList<>();
        if (actionRequired) {
            actions.add("생산 병목과 설비 상태를 함께 점검하고 저하 공정의 작업 순서를 재검토합니다.");
        }
        String summary = "생산 달성률은 %s이며 최근 달성률은 %s입니다.%s".formatted(
                percent(attainment), percent(latestAttainment),
                actionRequired ? " 생산량 저하 조사가 필요합니다." : " 급격한 생산 저하는 확인되지 않았습니다."
        );
        return new AgentResult(getName(), Intent.PRODUCTION, summary, evidence, actions, 0.88,
                elapsed(startedAt), AgentExecutionStatus.COMPLETED, null, actionRequired);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    private List<ProductionOrder> filter(List<ProductionOrder> values, String factoryId) {
        return factoryId == null ? values : values.stream().filter(value -> factoryId.equals(value.factoryId())).toList();
    }

    private AgentResult insufficient(long startedAt) {
        return new AgentResult(getName(), Intent.PRODUCTION, "판단할 생산 데이터가 부족함", List.of(), List.of(), 0,
                elapsed(startedAt), AgentExecutionStatus.INSUFFICIENT_DATA, null, false);
    }

    private double attainment(ProductionOrder order) {
        return order.targetQuantity() == 0 ? 0 : (double) order.producedQuantity() / order.targetQuantity();
    }

    private AgentEvidence evidence(String type, String description, String value, String source) {
        return new AgentEvidence(type, description, value, source);
    }

    private String percent(double value) {
        return "%.1f%%".formatted(value * 100);
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
