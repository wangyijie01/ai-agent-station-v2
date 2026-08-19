package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 新增或更新 Java 服务监督目标的请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JavaServiceTargetRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 64)
    private String serviceId;

    @NotBlank
    @Size(max = 100)
    private String serviceName;

    @Size(max = 32)
    private String environment;

    @NotBlank
    @Size(max = 512)
    private String baseUrl;

    @Size(max = 128)
    private String healthPath;

    @Size(max = 64)
    private String agentId;

    private Boolean enabled;

    @Min(5)
    @Max(3600)
    private Integer intervalSeconds;

    @Min(200)
    @Max(30000)
    private Integer timeoutMs;

    @Min(1)
    @Max(10)
    private Integer failureThreshold;

    @Min(1)
    @Max(60000)
    private Long slowThresholdMs;
}
