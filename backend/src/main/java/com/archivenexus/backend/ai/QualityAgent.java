package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentEvidence;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentExecutionStatus;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.QualityInspection;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QualityAgent implements ManufacturingAgent {
    private final NexusStateService nexus;

    public QualityAgent(NexusStateService nexus) {
        this.nexus = nexus;
    }

    @Override
    public String getName() {
        return "QualityAgent";
    }

    @Override
    public boolean supports(Intent intent) {
        return intent == Intent.QUALITY;
    }

    @Override
    public AgentResult analyze(AgentContext context) {
        long startedAt = System.nanoTime();
        List<QualityInspection> inspections = filter(nexus.inspections(), context.selectedFactoryId());
        if (inspections.isEmpty()) {
            return new AgentResult(getName(), Intent.QUALITY, "판단할 품질 데이터가 부족함", List.of(), List.of(), 0,
                    elapsed(startedAt), AgentExecutionStatus.INSUFFICIENT_DATA, null, false);
        }

        double average = inspections.stream().mapToDouble(QualityInspection::defectRate).average().orElse(0);
        double latest = inspections.get(inspections.size() - 1).defectRate();
        double maximum = inspections.stream().mapToDouble(QualityInspection::defectRate).max().orElse(0);
        long qualityAlerts = qualityAlerts(context.selectedFactoryId());
        boolean actionRequired = latest >= 0.03 || maximum >= 0.04;
        List<String> actions = actionRequired
                ? List.of("해당 Lot의 출하를 보류하고 공정 조건과 원자재 이력을 재검토합니다.")
                : List.of();
        String summary = "평균 불량률은 %s, 최근 불량률은 %s이며 품질 경보는 %d건입니다.%s".formatted(
                percent(average), percent(latest), qualityAlerts,
                actionRequired ? " 품질 조치가 필요합니다." : " 현재 품질 수준은 관리 범위입니다."
        );
        return new AgentResult(
                getName(), Intent.QUALITY, summary,
                List.of(
                        new AgentEvidence("average_defect_rate", "평균 불량률", percent(average), "qualityInspections"),
                        new AgentEvidence("latest_defect_rate", "최근 Lot 불량률", percent(latest), inspections.get(inspections.size() - 1).id()),
                        new AgentEvidence("quality_alerts", "품질 이상 이벤트", String.valueOf(qualityAlerts), "factoryAlerts")
                ),
                actions, 0.9, elapsed(startedAt), AgentExecutionStatus.COMPLETED, null, actionRequired
        );
    }

    @Override
    public int getPriority() {
        return 20;
    }

    private List<QualityInspection> filter(List<QualityInspection> values, String factoryId) {
        return factoryId == null ? values : values.stream().filter(value -> factoryId.equals(value.factoryId())).toList();
    }

    private long qualityAlerts(String factoryId) {
        return nexus.factories().stream()
                .filter(factory -> factoryId == null || factory.id().equals(factoryId))
                .flatMap(factory -> nexus.alerts(factory.id()).stream())
                .filter(alert -> "QUALITY".equals(alert.category()))
                .map(FactoryAlert::id)
                .distinct()
                .count();
    }

    private String percent(double value) {
        return "%.2f%%".formatted(value * 100);
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
