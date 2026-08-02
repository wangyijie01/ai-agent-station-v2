package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 监督节点的结构化质量门禁。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSupervisionDecision {

    public enum Verdict {
        PASS,
        OPTIMIZE,
        FAIL
    }

    @Builder.Default
    private Verdict verdict = Verdict.OPTIMIZE;

    @Builder.Default
    private int score = 0;

    private String assessment;
    private String nextAction;

    @Builder.Default
    private List<String> issues = new ArrayList<>();

    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    @Builder.Default
    private boolean fallbackParsed = false;
}
