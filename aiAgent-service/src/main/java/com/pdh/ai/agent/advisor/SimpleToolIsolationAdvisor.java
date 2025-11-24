package com.pdh.ai.agent.advisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.Ordered;

/**
 * Simple marker advisor to help identify different workflow contexts.
 * This helps with debugging tool isolation issues and workflow separation.
 * 
 * @author BookingSmart AI Team
 */
public class SimpleToolIsolationAdvisor implements Advisor {

    private final String workflowType;
    private final int order;

    public SimpleToolIsolationAdvisor(String workflowType, int order) {
        this.workflowType = workflowType;
        this.order = order;
    }

    public static SimpleToolIsolationAdvisor forRouting() {
        return new SimpleToolIsolationAdvisor("routing", Ordered.HIGHEST_PRECEDENCE + 10);
    }

    public static SimpleToolIsolationAdvisor forParallel() {
        return new SimpleToolIsolationAdvisor("parallel", Ordered.HIGHEST_PRECEDENCE + 50);
    }

    @Override
    public String getName() {
        return "ToolIsolationAdvisor-" + workflowType;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public String getWorkflowType() {
        return workflowType;
    }
}