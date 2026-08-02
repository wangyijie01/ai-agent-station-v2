package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动或恢复 AutoAgent 运行的请求。
 *
 * <p>V2 在原有会话参数之外增加了幂等、Skill 路由和工具治理字段。生产环境中，
 * {@link #approvedToolNames} 应由可信审批服务写入，不能直接信任普通客户端输入。</p>
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * @author wangyijie01
 * @since 2.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoAgentRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** AI 智能体 ID。 */
    private String aiAgentId;

    /** 本轮用户输入。 */
    private String message;

    /** 业务会话 ID，用于串联多轮对话。 */
    private String sessionId;

    /** 最大推理/执行步数。 */
    private Integer maxStep;

    /** 客户端生成的幂等键；同一次业务请求重试时必须保持不变。 */
    private String idempotencyKey;

    /** 需要从等待输入或失败状态继续执行时传入的 runId。 */
    private String resumeRunId;

    /** 显式请求的 Skill ID；为空时由路由器根据消息自动选择。 */
    @Builder.Default
    private List<String> requestedSkillIds = new ArrayList<>();

    /** 已获批准的高风险工具名；生产环境必须由可信授权链路提供。 */
    @Builder.Default
    private List<String> approvedToolNames = new ArrayList<>();

    /** 本次运行允许的工具调用总预算。 */
    private Integer maxToolCalls;

}
