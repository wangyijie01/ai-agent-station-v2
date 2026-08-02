package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行级评测结果。指标由可审计事件计算，不依赖“感觉回答不错”。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvaluation {

    private String runId;
    private AgentRunStatus status;
    private double score;
    private long durationMs;
    private int totalEvents;
    private int executionSteps;
    private int toolCalls;
    private int toolFailures;
    private boolean finalAnswerProduced;
    private boolean humanInputRequired;

    @Builder.Default
    private List<String> findings = new ArrayList<>();
}
