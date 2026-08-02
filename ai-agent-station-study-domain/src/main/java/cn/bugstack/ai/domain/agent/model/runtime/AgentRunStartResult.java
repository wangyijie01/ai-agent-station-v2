package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启动结果区分新运行、幂等重放和检查点恢复。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunStartResult {

    private AgentRun run;
    private boolean replay;
    private boolean resumed;
}
