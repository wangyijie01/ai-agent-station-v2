package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRunStartResult;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunServiceTest {

    private AgentRunService runService;

    @BeforeEach
    void setUp() {
        AgentSkillRegistry registry = new AgentSkillRegistry();
        registry.reload();
        AgentSkillRouter router = new AgentSkillRouter(registry);
        runService = new AgentRunService(router, registry, new AgentPromptContextBuilder());
    }

    @Test
    void shouldCreateRunAndReplaySameIdempotencyKey() {
        ExecuteCommandEntity first = command("same-key");
        AgentRunStartResult created = runService.start(first, "auto");
        ExecuteCommandEntity duplicate = command("same-key");
        AgentRunStartResult replayed = runService.start(duplicate, "auto");

        assertThat(created.isReplay()).isFalse();
        assertThat(replayed.isReplay()).isTrue();
        assertThat(replayed.getRun().getRunId()).isEqualTo(created.getRun().getRunId());
        assertThat(duplicate.getTraceId()).isEqualTo(first.getTraceId());
        assertThat(created.getRun().getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(created.getRun().getSelectedSkillIds()).contains("log-root-cause");
    }

    @Test
    void shouldWaitAndResumeFromCheckpoint() {
        ExecuteCommandEntity first = command("resume-key");
        runService.start(first, "auto");
        runService.checkpoint(first, "analysis", 1, "缺少时间窗", "请补充");
        runService.markWaiting(first, null, "请提供环境和时间窗", "缺少 environment、timeRange");

        ExecuteCommandEntity resumedCommand = command("new-key");
        resumedCommand.setResumeRunId(first.getRunId());
        resumedCommand.setMessage("生产环境，最近 30 分钟");
        AgentRunStartResult resumed = runService.start(resumedCommand, "auto");

        assertThat(resumed.isResumed()).isTrue();
        assertThat(resumed.getRun().getRunId()).isEqualTo(first.getRunId());
        assertThat(resumed.getRun().getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(resumedCommand.getResumeContext())
                .contains("WAIT_USER_INPUT", "缺少 environment、timeRange", "生产环境，最近 30 分钟");
    }

    @Test
    void terminalStateMustNotTransitionBackToRunning() {
        assertThat(AgentRunStatus.SUCCEEDED.canTransitionTo(AgentRunStatus.RUNNING)).isFalse();
        assertThat(AgentRunStatus.FAILED.canTransitionTo(AgentRunStatus.RUNNING)).isTrue();
        assertThat(AgentRunStatus.RUNNING.canTransitionTo(AgentRunStatus.WAITING_USER_INPUT)).isTrue();
        assertThatThrownBy(() -> runService.start(ExecuteCommandEntity.builder().build(), "auto"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExecuteCommandEntity command(String key) {
        return ExecuteCommandEntity.builder()
                .aiAgentId("agent-001")
                .sessionId("session-001")
                .message("查询 TraceId 对应日志异常根因")
                .idempotencyKey(key)
                .maxStep(3)
                .maxToolCalls(5)
                .build();
    }
}
