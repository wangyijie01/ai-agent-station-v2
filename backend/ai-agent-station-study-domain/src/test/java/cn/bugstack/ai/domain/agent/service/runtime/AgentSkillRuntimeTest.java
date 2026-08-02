package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.SkillMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSkillRuntimeTest {

    private AgentSkillRegistry registry;
    private AgentSkillRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentSkillRegistry();
        registry.reload();
        router = new AgentSkillRouter(registry);
    }

    @Test
    void shouldLoadVersionedSkillsFromClasspath() {
        assertThat(registry.list()).hasSize(6);
        assertThat(registry.findById("log-root-cause")).isPresent();
        assertThat(registry.findById("incident-response").orElseThrow().getAllowedTools()).isNotEmpty();
    }

    @Test
    void shouldRouteByTriggerWithExplainableReasons() {
        List<SkillMatch> matches = router.route("请结合 TraceId 查询 ELK 日志并定位异常根因", List.of(), 3);

        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).getSkill().getId()).isEqualTo("log-root-cause");
        assertThat(matches.get(0).getReasons()).anyMatch(reason -> reason.startsWith("trigger:"));
    }

    @Test
    void shouldHonorExplicitSkillSelection() {
        List<SkillMatch> matches = router.route("帮我处理一下", List.of("sql-performance"), 1);

        assertThat(matches).singleElement()
                .extracting(match -> match.getSkill().getId())
                .isEqualTo("sql-performance");
        assertThat(matches.get(0).getReasons()).contains("explicit:sql-performance");
    }
}
