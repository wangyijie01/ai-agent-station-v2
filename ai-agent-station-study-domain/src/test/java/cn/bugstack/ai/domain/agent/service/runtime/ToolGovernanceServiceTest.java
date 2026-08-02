package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolGovernanceServiceTest {

    private AgentRunService runService;
    private ToolGovernanceService governance;

    @BeforeEach
    void setUp() {
        AgentSkillRegistry registry = new AgentSkillRegistry();
        registry.reload();
        runService = new AgentRunService(new AgentSkillRouter(registry), registry, new AgentPromptContextBuilder());
        governance = new ToolGovernanceService(runService);
    }

    @Test
    void shouldFailClosedWithoutTrustedRunContext() {
        ToolCallback wrapped = governance.wrap(new ToolCallback[]{tool("search_logs", "read logs")})[0];

        assertThatThrownBy(() -> wrapped.call("{}"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("runId");
        assertThat(governance.audits("unknown")).singleElement()
                .extracting(record -> record.getDecision())
                .isEqualTo("DENY_NO_RUN_CONTEXT");
    }

    @Test
    void shouldEnforceAllowlistAndToolBudget() {
        ExecuteCommandEntity command = start("log-root-cause");
        ToolCallback wrapped = governance.wrap(new ToolCallback[]{tool("search_logs", "read logs")})[0];
        ToolContext context = context(command, List.of(), 1);

        assertThat(wrapped.call("{\"traceId\":\"abc\"}", context)).isEqualTo("ok-1");
        assertThatThrownBy(() -> wrapped.call("{\"traceId\":\"def\"}", context))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("预算");
        assertThat(governance.audits(command.getRunId())).hasSize(2);
    }

    @Test
    void highRiskToolMustHaveExplicitApprovalAndIsIdempotent() {
        ExecuteCommandEntity command = start("incident-response");
        ToolCallback wrapped = governance.wrap(new ToolCallback[]{tool("rollback_service", "rollback release")})[0];

        assertThatThrownBy(() -> wrapped.call("{\"service\":\"order\"}", context(command, List.of(), 4)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("人工批准");

        ToolContext approved = context(command, List.of("rollback_service"), 4);
        assertThat(wrapped.call("{\"service\":\"order\"}", approved)).isEqualTo("ok-1");
        assertThat(wrapped.call("{\"service\":\"order\"}", approved)).isEqualTo("ok-1");
        assertThat(governance.audits(command.getRunId()))
                .anyMatch(record -> record.isIdempotencyHit() && record.isSuccess());
    }

    private ExecuteCommandEntity start(String skillId) {
        ExecuteCommandEntity command = ExecuteCommandEntity.builder()
                .aiAgentId("agent-001")
                .sessionId("session-001")
                .message("execute test")
                .idempotencyKey(skillId + System.nanoTime())
                .requestedSkillIds(List.of(skillId))
                .maxToolCalls(4)
                .build();
        runService.start(command, "auto");
        return command;
    }

    private static ToolContext context(ExecuteCommandEntity command, List<String> approved, int budget) {
        return new ToolContext(Map.of(
                "agent.run_id", command.getRunId(),
                "agent.trace_id", command.getTraceId(),
                "agent.allowed_tools", command.getAllowedTools(),
                "agent.approved_tools", approved,
                "agent.max_tool_calls", budget));
    }

    private static ToolCallback tool(String name, String description) {
        AtomicInteger calls = new AtomicInteger();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "ok-" + calls.incrementAndGet();
            }
        };
    }
}
