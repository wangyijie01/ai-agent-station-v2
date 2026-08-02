package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.runtime.AgentCheckpoint;
import cn.bugstack.ai.domain.agent.model.runtime.AgentEvaluation;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRun;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRunEvent;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRunStartResult;
import cn.bugstack.ai.domain.agent.model.runtime.AgentRunStatus;
import cn.bugstack.ai.domain.agent.model.runtime.AgentSkillDefinition;
import cn.bugstack.ai.domain.agent.model.runtime.SkillMatch;
import com.alibaba.fastjson2.JSON;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Agent 运行时与事件存储。
 *
 * <p>当前提供有界内存实现，接口语义已经覆盖幂等、事件重放、检查点、恢复和取消；
 * 生产环境可以按 docs/sql/agent-runtime-v2.sql 替换为 MySQL/Redis 实现。</p>
 */
@Slf4j
@Service
public class AgentRunService {

    private static final int MAX_RUNS = 1_000;

    private final AgentSkillRouter skillRouter;
    private final AgentSkillRegistry skillRegistry;
    private final AgentPromptContextBuilder promptContextBuilder;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    private final Map<String, RunAggregate> runs = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public AgentRunService(AgentSkillRouter skillRouter,
                           AgentSkillRegistry skillRegistry,
                           AgentPromptContextBuilder promptContextBuilder) {
        this(skillRouter, skillRegistry, promptContextBuilder, Clock.systemUTC(), null);
    }

    @Autowired
    public AgentRunService(AgentSkillRouter skillRouter,
                           AgentSkillRegistry skillRegistry,
                           AgentPromptContextBuilder promptContextBuilder,
                           MeterRegistry meterRegistry) {
        this(skillRouter, skillRegistry, promptContextBuilder, Clock.systemUTC(), meterRegistry);
    }

    AgentRunService(AgentSkillRouter skillRouter,
                    AgentSkillRegistry skillRegistry,
                    AgentPromptContextBuilder promptContextBuilder,
                    Clock clock) {
        this(skillRouter, skillRegistry, promptContextBuilder, clock, null);
    }

    AgentRunService(AgentSkillRouter skillRouter,
                    AgentSkillRegistry skillRegistry,
                    AgentPromptContextBuilder promptContextBuilder,
                    Clock clock,
                    MeterRegistry meterRegistry) {
        this.skillRouter = skillRouter;
        this.skillRegistry = skillRegistry;
        this.promptContextBuilder = promptContextBuilder;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public synchronized AgentRunStartResult start(ExecuteCommandEntity command, String strategy) {
        validate(command);
        if (command.getResumeRunId() != null && !command.getResumeRunId().isBlank()) {
            return resume(command);
        }

        String indexKey = idempotencyIndexKey(command);
        if (indexKey != null) {
            String existingRunId = idempotencyIndex.get(indexKey);
            if (existingRunId != null) {
                RunAggregate existing = runs.get(existingRunId);
                if (existing != null) {
                    populateRuntimeCommand(command, existing.run);
                    return AgentRunStartResult.builder().run(copy(existing.run)).replay(true).resumed(false).build();
                }
            }
        }

        List<SkillMatch> matches = skillRouter.route(command.getMessage(), command.getRequestedSkillIds(), 3);
        List<AgentSkillDefinition> selectedSkills = matches.stream().map(SkillMatch::getSkill).toList();
        Set<String> allowedTools = new LinkedHashSet<>();
        selectedSkills.forEach(skill -> allowedTools.addAll(skill.getAllowedTools()));

        long now = clock.millis();
        AgentRun run = AgentRun.builder()
                .runId(UUID.randomUUID().toString())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .sessionId(command.getSessionId())
                .agentId(command.getAiAgentId())
                .strategy(strategy)
                .idempotencyKey(command.getIdempotencyKey())
                .originalMessage(command.getMessage())
                .latestUserMessage(command.getMessage())
                .status(AgentRunStatus.CREATED)
                .selectedSkillIds(selectedSkills.stream().map(AgentSkillDefinition::getId).toList())
                .allowedTools(List.copyOf(allowedTools))
                .currentStep(0)
                .maxStep(command.getMaxStep() == null ? 3 : Math.max(1, command.getMaxStep()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        transition(run, AgentRunStatus.RUNNING);
        runs.put(run.getRunId(), new RunAggregate(run));
        if (indexKey != null) {
            idempotencyIndex.put(indexKey, run.getRunId());
        }
        populateRuntimeCommand(command, run);
        command.setSkillContext(promptContextBuilder.buildSkillContext(selectedSkills));
        cleanupIfNeeded();
        return AgentRunStartResult.builder().run(copy(run)).replay(false).resumed(false).build();
    }

    public void publishStarted(ExecuteCommandEntity command, ResponseBodyEmitter emitter, boolean resumed) {
        publishRuntimeEvent(command, emitter, resumed ? "run_resumed" : "run_started", "runtime",
                0, resumed ? "已从检查点恢复执行" : "Agent 运行已创建", false);
        publishRuntimeEvent(command, emitter, "skill_selected", "runtime", 0,
                command.getSelectedSkillIds().isEmpty()
                        ? "未命中专用 Skill，工具默认关闭"
                        : "已选择 Skills: " + command.getSelectedSkillIds(), false);
    }

    public AgentRunEvent publish(ExecuteCommandEntity command,
                                 ResponseBodyEmitter emitter,
                                 AutoAgentExecuteResultEntity result) {
        RunAggregate aggregate = required(command.getRunId());
        AgentRunEvent event;
        synchronized (aggregate) {
            long sequence = ++aggregate.sequence;
            long now = clock.millis();
            result.setEventId(UUID.randomUUID().toString());
            result.setRunId(command.getRunId());
            result.setTraceId(command.getTraceId());
            result.setSequence(sequence);
            result.setRunStatus(aggregate.run.getStatus().name());
            if (result.getTimestamp() == null) {
                result.setTimestamp(now);
            }
            event = AgentRunEvent.builder()
                    .eventId(result.getEventId())
                    .runId(command.getRunId())
                    .traceId(command.getTraceId())
                    .sequence(sequence)
                    .type(result.getType())
                    .subType(result.getSubType())
                    .step(result.getStep())
                    .content(result.getContent())
                    .runStatus(aggregate.run.getStatus())
                    .timestamp(result.getTimestamp())
                    .build();
            aggregate.events.add(event);
            aggregate.run.setEventCount(aggregate.events.size());
            if (result.getStep() != null) {
                aggregate.run.setCurrentStep(Math.max(aggregate.run.getCurrentStep(), result.getStep()));
            }
            aggregate.run.setUpdatedAt(now);
        }
        send(emitter, result);
        return event;
    }

    public AgentRunEvent publishRuntimeEvent(ExecuteCommandEntity command,
                                             ResponseBodyEmitter emitter,
                                             String type,
                                             String subType,
                                             Integer step,
                                             String content,
                                             boolean completed) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.builder()
                .type(type)
                .subType(subType)
                .step(step)
                .content(content)
                .completed(completed)
                .timestamp(clock.millis())
                .sessionId(command.getSessionId())
                .build();
        return publish(command, emitter, result);
    }

    public void checkpoint(ExecuteCommandEntity command,
                           String stage,
                           int step,
                           String executionSummary,
                           String nextAction) {
        RunAggregate aggregate = required(command.getRunId());
        synchronized (aggregate) {
            aggregate.checkpoints.add(AgentCheckpoint.builder()
                    .runId(command.getRunId())
                    .stage(stage)
                    .step(step)
                    .status(aggregate.run.getStatus())
                    .executionSummary(limit(executionSummary, 8_000))
                    .nextAction(limit(nextAction, 2_000))
                    .timestamp(clock.millis())
                    .build());
            aggregate.run.setCurrentStep(Math.max(aggregate.run.getCurrentStep(), step));
            aggregate.run.setUpdatedAt(clock.millis());
        }
    }

    public void markWaiting(ExecuteCommandEntity command,
                            ResponseBodyEmitter emitter,
                            String question,
                            String summary) {
        RunAggregate aggregate = required(command.getRunId());
        synchronized (aggregate) {
            transition(aggregate.run, AgentRunStatus.WAITING_USER_INPUT);
            aggregate.run.setWaitingQuestion(question);
        }
        checkpoint(command, "WAIT_USER_INPUT", aggregate.run.getCurrentStep(), summary, question);
        publishRuntimeEvent(command, emitter, "waiting_user_input", "clarification",
                aggregate.run.getCurrentStep(), question, false);
    }

    public void complete(ExecuteCommandEntity command, ResponseBodyEmitter emitter) {
        RunAggregate aggregate = required(command.getRunId());
        synchronized (aggregate) {
            if (aggregate.run.getStatus() == AgentRunStatus.WAITING_USER_INPUT
                    || aggregate.run.getStatus() == AgentRunStatus.CANCELED) {
                return;
            }
            transition(aggregate.run, AgentRunStatus.SUCCEEDED);
            aggregate.run.setCompletedAt(clock.millis());
        }
        publishRuntimeEvent(command, emitter, "complete", "runtime", null, "执行完成", true);
    }

    public void fail(ExecuteCommandEntity command, ResponseBodyEmitter emitter, Throwable error) {
        RunAggregate aggregate = required(command.getRunId());
        synchronized (aggregate) {
            if (!aggregate.run.getStatus().isTerminal()) {
                transition(aggregate.run, AgentRunStatus.FAILED);
            }
            aggregate.run.setErrorMessage(safeError(error));
            aggregate.run.setCompletedAt(clock.millis());
        }
        publishRuntimeEvent(command, emitter, "error", "runtime", null,
                "执行失败: " + safeError(error), true);
    }

    public void recordToolEvent(String runId, String toolName, boolean success, long durationMs, String decision) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        RunAggregate aggregate = runs.get(runId);
        if (aggregate == null) {
            return;
        }
        synchronized (aggregate) {
            long sequence = ++aggregate.sequence;
            AgentRunEvent event = AgentRunEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .runId(runId)
                    .traceId(aggregate.run.getTraceId())
                    .sequence(sequence)
                    .type("tool")
                    .subType(success ? "tool_success" : "tool_failure")
                    .step(aggregate.run.getCurrentStep())
                    .content(toolName)
                    .runStatus(aggregate.run.getStatus())
                    .timestamp(clock.millis())
                    .metadata(Map.of("durationMs", durationMs, "decision", decision))
                    .build();
            aggregate.events.add(event);
            aggregate.run.setEventCount(aggregate.events.size());
            aggregate.run.setUpdatedAt(clock.millis());
        }
        if (meterRegistry != null) {
            meterRegistry.counter("agent_tool_calls_total",
                    "tool", toolName, "success", Boolean.toString(success), "decision", decision).increment();
            meterRegistry.timer("agent_tool_call_duration", "tool", toolName)
                    .record(Duration.ofMillis(Math.max(0, durationMs)));
        }
    }

    public Optional<AgentRun> find(String runId) {
        RunAggregate aggregate = runs.get(runId);
        return aggregate == null ? Optional.empty() : Optional.of(copy(aggregate.run));
    }

    public List<AgentRunEvent> events(String runId) {
        RunAggregate aggregate = required(runId);
        synchronized (aggregate) {
            return List.copyOf(aggregate.events);
        }
    }

    public List<AgentCheckpoint> checkpoints(String runId) {
        RunAggregate aggregate = required(runId);
        synchronized (aggregate) {
            return List.copyOf(aggregate.checkpoints);
        }
    }

    public AgentEvaluation evaluate(String runId) {
        RunAggregate aggregate = required(runId);
        synchronized (aggregate) {
            List<AgentRunEvent> events = aggregate.events;
            int toolCalls = (int) events.stream().filter(event -> "tool".equals(event.getType())).count();
            int toolFailures = (int) events.stream().filter(event -> "tool_failure".equals(event.getSubType())).count();
            int steps = events.stream().filter(event -> event.getStep() != null)
                    .map(AgentRunEvent::getStep).max(Integer::compareTo).orElse(0);
            boolean finalAnswer = events.stream().anyMatch(event -> "summary".equals(event.getType()));
            boolean waiting = events.stream().anyMatch(event -> "waiting_user_input".equals(event.getType()));
            long end = aggregate.run.getCompletedAt() == null ? clock.millis() : aggregate.run.getCompletedAt();
            double score = 0;
            List<String> findings = new ArrayList<>();
            if (aggregate.run.getStatus() == AgentRunStatus.SUCCEEDED) {
                score += 55;
            } else if (waiting) {
                score += 25;
                findings.add("任务正确暂停并请求人工补充信息");
            } else {
                findings.add("运行未成功完成");
            }
            if (finalAnswer) {
                score += 25;
            } else if (!waiting) {
                findings.add("未产生最终答案事件");
            }
            if (toolFailures == 0) {
                score += 10;
            } else {
                findings.add("存在 " + toolFailures + " 次工具失败");
            }
            if (!aggregate.run.getSelectedSkillIds().isEmpty()) {
                score += 10;
            } else {
                findings.add("未命中专用 Skill");
            }
            return AgentEvaluation.builder()
                    .runId(runId)
                    .status(aggregate.run.getStatus())
                    .score(Math.min(score, 100))
                    .durationMs(Math.max(0, end - aggregate.run.getCreatedAt()))
                    .totalEvents(events.size())
                    .executionSteps(steps)
                    .toolCalls(toolCalls)
                    .toolFailures(toolFailures)
                    .finalAnswerProduced(finalAnswer)
                    .humanInputRequired(waiting)
                    .findings(findings)
                    .build();
        }
    }

    public void replay(String runId, ResponseBodyEmitter emitter) {
        for (AgentRunEvent event : events(runId)) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.builder()
                    .type(event.getType())
                    .subType(event.getSubType())
                    .step(event.getStep())
                    .content(event.getContent())
                    .completed(event.getRunStatus().isTerminal())
                    .timestamp(event.getTimestamp())
                    .sessionId(find(runId).map(AgentRun::getSessionId).orElse(null))
                    .eventId(event.getEventId())
                    .runId(event.getRunId())
                    .traceId(event.getTraceId())
                    .sequence(event.getSequence())
                    .runStatus(event.getRunStatus().name())
                    .build();
            send(emitter, result);
        }
    }

    public void attachFuture(String runId, Future<?> future) {
        futures.put(runId, future);
    }

    public boolean cancel(String runId) {
        RunAggregate aggregate = required(runId);
        synchronized (aggregate) {
            if (aggregate.run.getStatus().isTerminal()) {
                return false;
            }
            transition(aggregate.run, AgentRunStatus.CANCELED);
            aggregate.run.setCompletedAt(clock.millis());
        }
        Future<?> future = futures.remove(runId);
        if (future != null) {
            future.cancel(true);
        }
        recordSystemEvent(runId, "run_canceled", "用户取消运行");
        return true;
    }

    private AgentRunStartResult resume(ExecuteCommandEntity command) {
        RunAggregate aggregate = required(command.getResumeRunId());
        synchronized (aggregate) {
            AgentRunStatus status = aggregate.run.getStatus();
            if (status != AgentRunStatus.WAITING_USER_INPUT && status != AgentRunStatus.FAILED) {
                throw new IllegalStateException("仅 WAITING_USER_INPUT 或 FAILED 状态可恢复，当前状态: " + status);
            }
            transition(aggregate.run, AgentRunStatus.RUNNING);
            aggregate.run.setLatestUserMessage(command.getMessage());
            aggregate.run.setWaitingQuestion(null);
            aggregate.run.setErrorMessage(null);
            aggregate.run.setCompletedAt(null);
            aggregate.run.setResumeCount(aggregate.run.getResumeCount() + 1);
            aggregate.run.setUpdatedAt(clock.millis());
            populateRuntimeCommand(command, aggregate.run);
            List<AgentSkillDefinition> skills = skillRegistry.resolve(aggregate.run.getSelectedSkillIds());
            command.setSkillContext(promptContextBuilder.buildSkillContext(skills));
            if (!aggregate.checkpoints.isEmpty()) {
                AgentCheckpoint checkpoint = aggregate.checkpoints.get(aggregate.checkpoints.size() - 1);
                command.setResumeContext("阶段=" + checkpoint.getStage() + "，步骤=" + checkpoint.getStep()
                        + "\n历史摘要=" + checkpoint.getExecutionSummary()
                        + "\n待执行=" + checkpoint.getNextAction()
                        + "\n用户补充=" + command.getMessage());
            }
            String indexKey = idempotencyIndexKey(command);
            if (indexKey != null) {
                idempotencyIndex.put(indexKey, aggregate.run.getRunId());
            }
            return AgentRunStartResult.builder().run(copy(aggregate.run)).replay(false).resumed(true).build();
        }
    }

    private void populateRuntimeCommand(ExecuteCommandEntity command, AgentRun run) {
        command.setRunId(run.getRunId());
        command.setTraceId(run.getTraceId());
        command.setSelectedSkillIds(List.copyOf(run.getSelectedSkillIds()));
        command.setAllowedTools(List.copyOf(run.getAllowedTools()));
        if (command.getMaxStep() == null) {
            command.setMaxStep(run.getMaxStep());
        }
        if (command.getMaxToolCalls() == null) {
            command.setMaxToolCalls(8);
        }
        if (command.getSkillContext() == null) {
            command.setSkillContext(promptContextBuilder.buildSkillContext(
                    skillRegistry.resolve(run.getSelectedSkillIds())));
        }
    }

    private void recordSystemEvent(String runId, String type, String content) {
        RunAggregate aggregate = required(runId);
        synchronized (aggregate) {
            aggregate.events.add(AgentRunEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .runId(runId)
                    .traceId(aggregate.run.getTraceId())
                    .sequence(++aggregate.sequence)
                    .type(type)
                    .subType("runtime")
                    .content(content)
                    .runStatus(aggregate.run.getStatus())
                    .timestamp(clock.millis())
                    .build());
            aggregate.run.setEventCount(aggregate.events.size());
        }
    }

    private void transition(AgentRun run, AgentRunStatus target) {
        AgentRunStatus previous = run.getStatus();
        if (!run.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException("非法 Agent 状态转换: " + run.getStatus() + " -> " + target);
        }
        run.setStatus(target);
        run.setUpdatedAt(clock.millis());
        if (meterRegistry != null && previous != target) {
            meterRegistry.counter("agent_run_transitions_total",
                    "from", previous.name(), "to", target.name()).increment();
            if (target.isTerminal()) {
                meterRegistry.timer("agent_run_duration", "status", target.name())
                        .record(Duration.ofMillis(Math.max(0, clock.millis() - run.getCreatedAt())));
            }
        }
    }

    private RunAggregate required(String runId) {
        RunAggregate aggregate = runs.get(runId);
        if (aggregate == null) {
            throw new IllegalArgumentException("Agent run 不存在: " + runId);
        }
        return aggregate;
    }

    private static void validate(ExecuteCommandEntity command) {
        if (command == null || command.getAiAgentId() == null || command.getAiAgentId().isBlank()) {
            throw new IllegalArgumentException("aiAgentId 不能为空");
        }
        if (command.getMessage() == null || command.getMessage().isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        if (command.getSessionId() == null || command.getSessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (command.getMaxStep() != null && (command.getMaxStep() < 1 || command.getMaxStep() > 20)) {
            throw new IllegalArgumentException("maxStep 必须在 1~20 之间");
        }
        if (command.getMaxToolCalls() != null && (command.getMaxToolCalls() < 1 || command.getMaxToolCalls() > 100)) {
            throw new IllegalArgumentException("maxToolCalls 必须在 1~100 之间");
        }
    }

    private String idempotencyIndexKey(ExecuteCommandEntity command) {
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()) {
            return null;
        }
        return command.getSessionId() + ':' + command.getIdempotencyKey();
    }

    private void cleanupIfNeeded() {
        if (runs.size() <= MAX_RUNS) {
            return;
        }
        runs.values().stream()
                .filter(aggregate -> aggregate.run.getStatus().isTerminal())
                .min(Comparator.comparingLong(aggregate -> aggregate.run.getUpdatedAt()))
                .ifPresent(aggregate -> {
                    runs.remove(aggregate.run.getRunId());
                    futures.remove(aggregate.run.getRunId());
                    idempotencyIndex.values().removeIf(aggregate.run.getRunId()::equals);
                });
    }

    private static AgentRun copy(AgentRun run) {
        return AgentRun.builder()
                .runId(run.getRunId())
                .traceId(run.getTraceId())
                .sessionId(run.getSessionId())
                .agentId(run.getAgentId())
                .strategy(run.getStrategy())
                .idempotencyKey(run.getIdempotencyKey())
                .originalMessage(run.getOriginalMessage())
                .latestUserMessage(run.getLatestUserMessage())
                .status(run.getStatus())
                .selectedSkillIds(List.copyOf(run.getSelectedSkillIds()))
                .allowedTools(List.copyOf(run.getAllowedTools()))
                .currentStep(run.getCurrentStep())
                .maxStep(run.getMaxStep())
                .eventCount(run.getEventCount())
                .resumeCount(run.getResumeCount())
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .completedAt(run.getCompletedAt())
                .waitingQuestion(run.getWaitingQuestion())
                .errorMessage(run.getErrorMessage())
                .build();
    }

    private static String safeError(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : limit(message, 1_000);
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static void send(ResponseBodyEmitter emitter, AutoAgentExecuteResultEntity result) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE 客户端已断开，runId={}，eventId={}，原因={}",
                    result.getRunId(), result.getEventId(), e.getMessage());
        }
    }

    private static final class RunAggregate {
        private final AgentRun run;
        private final List<AgentRunEvent> events = new ArrayList<>();
        private final List<AgentCheckpoint> checkpoints = new ArrayList<>();
        private long sequence = 0;

        private RunAggregate(AgentRun run) {
            this.run = run;
        }
    }
}
