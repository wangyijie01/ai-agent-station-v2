package cn.bugstack.ai.domain.ops.model;

/** Java 服务在一次主动探测中的健康状态。 */
public enum ServiceHealthStatus {
    UP,
    DEGRADED,
    DOWN
}
