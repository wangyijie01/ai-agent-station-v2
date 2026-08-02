package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用审计记录。入参只保存摘要和哈希，避免把敏感原文写入日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolAuditRecord {

    private String auditId;
    private String runId;
    private String toolName;
    private AgentRiskLevel riskLevel;
    private String decision;
    private String inputHash;
    private String inputSummary;
    private boolean success;
    private boolean idempotencyHit;
    private int attempt;
    private long durationMs;
    private String errorMessage;
    private long timestamp;
}
