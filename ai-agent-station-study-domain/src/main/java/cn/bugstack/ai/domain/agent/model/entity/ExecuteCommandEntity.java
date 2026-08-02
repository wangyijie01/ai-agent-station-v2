package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行命令实体
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:46
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCommandEntity {

    private String aiAgentId;

    private String message;

    private String sessionId;

    private Integer maxStep;

    /** 客户端幂等键；同一 session 下重复提交会重放已有事件。 */
    private String idempotencyKey;

    /** 从 WAIT_USER_INPUT / FAILED 检查点恢复的 runId。 */
    private String resumeRunId;

    /** 用户显式指定的 Skill；为空时由路由器选择。 */
    @Builder.Default
    private List<String> requestedSkillIds = new ArrayList<>();

    /** 本轮已人工批准的高风险工具名。 */
    @Builder.Default
    private List<String> approvedToolNames = new ArrayList<>();

    /** 单次运行最多允许调用的工具次数。 */
    private Integer maxToolCalls;

    /** 以下字段由运行时服务填充，不由客户端直接信任。 */
    private String runId;
    private String traceId;

    @Builder.Default
    private List<String> selectedSkillIds = new ArrayList<>();

    @Builder.Default
    private List<String> allowedTools = new ArrayList<>();

    private String skillContext;
    private String resumeContext;

}
