package com.archivenexus.backend.ai;

import com.archivenexus.backend.ai.ManufacturingAiModels.AgentContext;
import com.archivenexus.backend.ai.ManufacturingAiModels.AgentResult;
import com.archivenexus.backend.ai.ManufacturingAiModels.Intent;

public interface ManufacturingAgent {
    String getName();

    boolean supports(Intent intent);

    AgentResult analyze(AgentContext context);

    int getPriority();
}
