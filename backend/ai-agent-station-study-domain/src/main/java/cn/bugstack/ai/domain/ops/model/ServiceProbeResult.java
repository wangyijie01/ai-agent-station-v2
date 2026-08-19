package cn.bugstack.ai.domain.ops.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一次健康探测的标准化结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceProbeResult {

    private String serviceId;
    private ServiceHealthStatus status;
    private Integer httpStatus;
    private long latencyMs;
    private long observedAt;
    private String summary;
    private String evidence;
    private int consecutiveFailures;
}
