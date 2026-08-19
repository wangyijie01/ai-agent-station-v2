package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Java 服务监督配置，可通过 YAML 或环境变量声明初始探测目标。 */
@Data
@ConfigurationProperties(prefix = "ai.ops.supervision")
public class OpsSupervisionProperties {

    private boolean enabled = true;
    private long schedulerDelayMs = 10_000;
    private List<Target> targets = new ArrayList<>();

    @Data
    public static class Target {
        private String serviceId;
        private String serviceName;
        private String environment = "local";
        private String baseUrl;
        private String healthPath = "/actuator/health";
        private String agentId;
        private boolean enabled = true;
        private int intervalSeconds = 30;
        private int timeoutMs = 3_000;
        private int failureThreshold = 3;
        private long slowThresholdMs = 1_500;
    }
}
