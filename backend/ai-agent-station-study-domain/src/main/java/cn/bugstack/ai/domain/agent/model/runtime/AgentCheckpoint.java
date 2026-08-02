package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可恢复执行的最小检查点。持久化实现可替换内存 RunStore，而不影响引擎。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCheckpoint {

    private String runId;
    private String stage;
    private int step;
    private AgentRunStatus status;
    private String executionSummary;
    private String nextAction;
    private long timestamp;
}
