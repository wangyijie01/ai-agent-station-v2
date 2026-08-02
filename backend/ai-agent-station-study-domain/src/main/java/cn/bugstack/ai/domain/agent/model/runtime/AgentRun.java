package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Agent 执行的可查询快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRun {

    private String runId;
    private String traceId;
    private String sessionId;
    private String agentId;
    private String strategy;
    private String idempotencyKey;
    private String originalMessage;
    private String latestUserMessage;

    @Builder.Default
    private AgentRunStatus status = AgentRunStatus.CREATED;

    @Builder.Default
    private List<String> selectedSkillIds = new ArrayList<>();

    @Builder.Default
    private List<String> allowedTools = new ArrayList<>();

    private int currentStep;
    private int maxStep;
    private int eventCount;
    private int resumeCount;
    private long createdAt;
    private long updatedAt;
    private Long completedAt;
    private String waitingQuestion;
    private String errorMessage;
}
