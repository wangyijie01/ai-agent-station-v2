package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.AgentRiskLevel;
import cn.bugstack.ai.domain.agent.model.runtime.ToolAuditRecord;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP/ToolCallback 治理层：默认拒绝、白名单、人工审批、预算、幂等、重试、熔断与审计。
 */
@Service
public class ToolGovernanceService {

    private static final int CIRCUIT_FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_OPEN_MS = Duration.ofSeconds(30).toMillis();

    private final AgentRunService runService;
    private final Map<String, AtomicInteger> runCallCounts = new ConcurrentHashMap<>();
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final Map<String, String> idempotentResults = new ConcurrentHashMap<>();
    private final Map<String, List<ToolAuditRecord>> audits = new ConcurrentHashMap<>();

    public ToolGovernanceService(AgentRunService runService) {
        this.runService = runService;
    }

    public ToolCallback[] wrap(ToolCallback[] callbacks) {
        if (callbacks == null || callbacks.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int index = 0; index < callbacks.length; index++) {
            wrapped[index] = new GovernedToolCallback(callbacks[index], this);
        }
        return wrapped;
    }

    String execute(ToolCallback delegate, String input, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
        String runId = stringValue(context.get("agent.run_id"));
        AgentRiskLevel risk = classify(toolName, delegate.getToolDefinition().description());
        String inputHash = sha256(input == null ? "" : input);
        String idempotencyKey = runId + ':' + toolName + ':' + inputHash;

        Authorization authorization = authorize(runId, toolName, risk, context);
        if (!authorization.allowed()) {
            audit(runId, toolName, risk, authorization.decision(), inputHash, input,
                    false, false, 0, 0, authorization.reason());
            runService.recordToolEvent(runId, toolName, false, 0, authorization.decision());
            throw new SecurityException(authorization.reason());
        }

        if (risk.ordinal() >= AgentRiskLevel.MEDIUM.ordinal()) {
            String cached = idempotentResults.get(idempotencyKey);
            if (cached != null) {
                audit(runId, toolName, risk, "IDEMPOTENCY_HIT", inputHash, input,
                        true, true, 0, 0, null);
                runService.recordToolEvent(runId, toolName, true, 0, "IDEMPOTENCY_HIT");
                return cached;
            }
        }

        CircuitState circuit = circuits.computeIfAbsent(toolName, ignored -> new CircuitState());
        if (circuit.openUntil > System.currentTimeMillis()) {
            String reason = "工具熔断中，请稍后重试: " + toolName;
            audit(runId, toolName, risk, "CIRCUIT_OPEN", inputHash, input,
                    false, false, 0, 0, reason);
            runService.recordToolEvent(runId, toolName, false, 0, "CIRCUIT_OPEN");
            throw new IllegalStateException(reason);
        }

        int maxAttempts = risk == AgentRiskLevel.READ_ONLY || risk == AgentRiskLevel.LOW ? 2 : 1;
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            try {
                String result = callDelegate(delegate, input, toolContext);
                long durationMs = elapsedMs(started);
                circuit.onSuccess();
                if (risk.ordinal() >= AgentRiskLevel.MEDIUM.ordinal()) {
                    idempotentResults.put(idempotencyKey, result);
                }
                audit(runId, toolName, risk, authorization.decision(), inputHash, input,
                        true, false, attempt, durationMs, null);
                runService.recordToolEvent(runId, toolName, true, durationMs, authorization.decision());
                return result;
            } catch (RuntimeException error) {
                long durationMs = elapsedMs(started);
                lastError = error;
                circuit.onFailure();
                audit(runId, toolName, risk, "TOOL_ERROR", inputHash, input,
                        false, false, attempt, durationMs, safeError(error));
                runService.recordToolEvent(runId, toolName, false, durationMs, "TOOL_ERROR");
            }
        }
        throw lastError == null ? new IllegalStateException("工具调用失败: " + toolName) : lastError;
    }

    public List<ToolAuditRecord> audits(String runId) {
        return List.copyOf(audits.getOrDefault(runId, List.of()));
    }

    public AgentRiskLevel classify(String toolName, String description) {
        String value = ((toolName == null ? "" : toolName) + ' ' + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        if (containsAny(value, "delete", "remove", "drop", "truncate", "publish", "send", "notify",
                "deploy", "release", "rollback", "restart", "execute", "shell", "write", "update", "create", "insert")) {
            return AgentRiskLevel.HIGH;
        }
        if (containsAny(value, "query", "search", "list", "get", "read", "fetch", "describe", "show", "explain", "inspect")) {
            return AgentRiskLevel.READ_ONLY;
        }
        return AgentRiskLevel.MEDIUM;
    }

    private Authorization authorize(String runId,
                                    String toolName,
                                    AgentRiskLevel risk,
                                    Map<String, Object> context) {
        if (runId == null || runId.isBlank() || runService.find(runId).isEmpty()) {
            return Authorization.deny("DENY_NO_RUN_CONTEXT", "缺少可信 runId，工具调用被拒绝");
        }
        List<String> allowedTools = stringList(context.get("agent.allowed_tools"));
        if (allowedTools.stream().noneMatch(pattern -> globMatches(pattern, toolName))) {
            return Authorization.deny("DENY_NOT_ALLOWLISTED", "工具不在本轮 Skill 白名单: " + toolName);
        }
        int maxToolCalls = intValue(context.get("agent.max_tool_calls"), 8);
        int used = runCallCounts.computeIfAbsent(runId, ignored -> new AtomicInteger()).incrementAndGet();
        if (used > maxToolCalls) {
            return Authorization.deny("DENY_BUDGET_EXCEEDED", "工具调用预算已耗尽: " + used + '/' + maxToolCalls);
        }
        if (risk == AgentRiskLevel.HIGH) {
            List<String> approved = stringList(context.get("agent.approved_tools"));
            if (approved.stream().noneMatch(pattern -> globMatches(pattern, toolName))) {
                return Authorization.deny("REQUIRE_HUMAN_APPROVAL", "高风险工具需要人工批准: " + toolName);
            }
            return Authorization.allow("ALLOW_APPROVED_HIGH_RISK");
        }
        return Authorization.allow("ALLOW_SKILL_POLICY");
    }

    private void audit(String runId,
                       String toolName,
                       AgentRiskLevel risk,
                       String decision,
                       String inputHash,
                       String input,
                       boolean success,
                       boolean idempotencyHit,
                       int attempt,
                       long durationMs,
                       String errorMessage) {
        ToolAuditRecord record = ToolAuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .runId(runId)
                .toolName(toolName)
                .riskLevel(risk)
                .decision(decision)
                .inputHash(inputHash)
                .inputSummary(redact(input))
                .success(success)
                .idempotencyHit(idempotencyHit)
                .attempt(attempt)
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
        audits.computeIfAbsent(runId == null ? "unknown" : runId,
                ignored -> java.util.Collections.synchronizedList(new ArrayList<>())).add(record);
    }

    private static boolean globMatches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        String regex = pattern.trim().toLowerCase(Locale.ROOT)
                .replace(".", "\\.")
                .replace("*", ".*");
        return value.toLowerCase(Locale.ROOT).matches(regex);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /**
     * Spring AI 允许 ToolCallback 不支持 ToolContext。治理上下文已经在真实调用前完成鉴权，
     * 因此仅当实现类确实覆写双参数方法时才向下透传，避免默认方法抛出异常。
     */
    private static String callDelegate(ToolCallback delegate, String input, ToolContext toolContext) {
        try {
            Method method = delegate.getClass().getMethod("call", String.class, ToolContext.class);
            return method.getDeclaringClass() == ToolCallback.class
                    ? delegate.call(input)
                    : delegate.call(input, toolContext);
        } catch (NoSuchMethodException impossible) {
            return delegate.call(input);
        }
    }

    private static String redact(String input) {
        if (input == null) {
            return "";
        }
        String value = input.replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^,}\\s]+", "$1=***");
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private static String safeError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算工具入参哈希", e);
        }
    }

    private record Authorization(boolean allowed, String decision, String reason) {
        private static Authorization allow(String decision) {
            return new Authorization(true, decision, null);
        }

        private static Authorization deny(String decision, String reason) {
            return new Authorization(false, decision, reason);
        }
    }

    private static final class CircuitState {
        private int failures;
        private long openUntil;

        private synchronized void onSuccess() {
            failures = 0;
            openUntil = 0;
        }

        private synchronized void onFailure() {
            failures++;
            if (failures >= CIRCUIT_FAILURE_THRESHOLD) {
                openUntil = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
                failures = 0;
            }
        }
    }
}
