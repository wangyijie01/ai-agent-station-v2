package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一运行事件。sequence 在单 run 内严格递增，可用于 SSE 重放和问题复盘。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunEvent {

    private String eventId;
    private String runId;
    private String traceId;
    private long sequence;
    private String type;
    private String subType;
    private Integer step;
    private String content;
    private AgentRunStatus runStatus;
    private long timestamp;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
