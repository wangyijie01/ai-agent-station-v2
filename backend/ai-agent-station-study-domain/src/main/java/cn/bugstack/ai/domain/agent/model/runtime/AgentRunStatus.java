package cn.bugstack.ai.domain.agent.model.runtime;

import java.util.EnumSet;
import java.util.Map;

/**
 * Agent 运行状态。状态转换集中在这里校验，避免各执行节点随意改状态。
 */
public enum AgentRunStatus {
    CREATED,
    RUNNING,
    WAITING_USER_INPUT,
    SUCCEEDED,
    FAILED,
    CANCELED;

    private static final Map<AgentRunStatus, EnumSet<AgentRunStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(RUNNING, CANCELED),
            RUNNING, EnumSet.of(WAITING_USER_INPUT, SUCCEEDED, FAILED, CANCELED),
            WAITING_USER_INPUT, EnumSet.of(RUNNING, CANCELED),
            FAILED, EnumSet.of(RUNNING, CANCELED),
            SUCCEEDED, EnumSet.noneOf(AgentRunStatus.class),
            CANCELED, EnumSet.noneOf(AgentRunStatus.class)
    );

    public boolean canTransitionTo(AgentRunStatus target) {
        return this == target || TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
