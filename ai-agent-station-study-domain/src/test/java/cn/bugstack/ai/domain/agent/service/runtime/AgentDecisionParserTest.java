package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.AgentAnalysisDecision;
import cn.bugstack.ai.domain.agent.model.runtime.AgentSupervisionDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDecisionParserTest {

    private final AgentDecisionParser parser = new AgentDecisionParser();

    @Test
    void shouldPreferStructuredAnalysisAndRecognizeWaitingState() {
        AgentAnalysisDecision decision = parser.parseAnalysis("""
                ```json
                {"status":"WAIT_USER_INPUT","progress":25,"summary":"缺少环境", 
                 "clarificationQuestion":"请提供环境和时间窗", "missingFields":["environment","timeRange"]}
                ```
                """);

        assertThat(decision.getStatus()).isEqualTo(AgentAnalysisDecision.Status.WAIT_USER_INPUT);
        assertThat(decision.getProgress()).isEqualTo(25);
        assertThat(decision.getMissingFields()).containsExactly("environment", "timeRange");
        assertThat(decision.isFallbackParsed()).isFalse();
    }

    @Test
    void shouldKeepLegacyOutputAsExplicitFallback() {
        AgentAnalysisDecision decision = parser.parseAnalysis("任务状态分析：继续执行，完成度评估：45%");

        assertThat(decision.getStatus()).isEqualTo(AgentAnalysisDecision.Status.CONTINUE);
        assertThat(decision.getProgress()).isEqualTo(45);
        assertThat(decision.isFallbackParsed()).isTrue();
    }

    @Test
    void shouldParseSupervisorVerdict() {
        AgentSupervisionDecision decision = parser.parseSupervision("""
                {"verdict":"PASS","score":92,"assessment":"证据充分","issues":[],"suggestions":["补充回滚指标"]}
                """);

        assertThat(decision.getVerdict()).isEqualTo(AgentSupervisionDecision.Verdict.PASS);
        assertThat(decision.getScore()).isEqualTo(92);
        assertThat(decision.getSuggestions()).containsExactly("补充回滚指标");
    }
}
