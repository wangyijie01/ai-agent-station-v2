package cn.bugstack.ai.domain.ops.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 被监督的传统 Java 服务。
 *
 * <p>平台只保存探测所需的非敏感信息，鉴权信息应通过网关或 MCP 服务管理，
 * 避免把令牌写入数据库和运行事件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JavaServiceTarget {

    private String serviceId;
    private String serviceName;
    private String environment;
    private String baseUrl;

    @Builder.Default
    private String healthPath = "/actuator/health";

    private String agentId;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private int intervalSeconds = 30;

    @Builder.Default
    private int timeoutMs = 3_000;

    @Builder.Default
    private int failureThreshold = 3;

    @Builder.Default
    private long slowThresholdMs = 1_500;

    private long createdAt;
    private long updatedAt;
}
