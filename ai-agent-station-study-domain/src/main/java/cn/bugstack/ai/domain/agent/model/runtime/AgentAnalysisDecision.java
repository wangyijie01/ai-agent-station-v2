package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析节点的结构化输出，替代对中文标题做 contains 判断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalysisDecision {

    public enum Status {
        CONTINUE,
        COMPLETED,
        WAIT_USER_INPUT
    }

    @Builder.Default
    private Status status = Status.CONTINUE;

    @Builder.Default
    private int progress = 0;

    private String summary;
    private String nextAction;
    private String clarificationQuestion;

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    @Builder.Default
    private boolean fallbackParsed = false;
}
