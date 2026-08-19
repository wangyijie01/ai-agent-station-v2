package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.service.OpsSupervisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 初始化配置文件中的 Java 服务监督目标。 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OpsSupervisionProperties.class)
public class OpsSupervisionConfiguration {

    @Bean
    public ApplicationRunner opsTargetInitializer(OpsSupervisionProperties properties,
                                                   OpsSupervisionService supervisionService) {
        return arguments -> {
            if (!properties.isEnabled()) {
                log.info("Java 服务智能监督已停用");
                return;
            }
            properties.getTargets().stream()
                    .filter(target -> target.getBaseUrl() != null && !target.getBaseUrl().isBlank())
                    .map(OpsSupervisionConfiguration::toDomain)
                    .forEach(supervisionService::register);
            log.info("Java 服务智能监督已加载 targetCount={}", supervisionService.listTargets().size());
        };
    }

    private static JavaServiceTarget toDomain(OpsSupervisionProperties.Target target) {
        return JavaServiceTarget.builder()
                .serviceId(target.getServiceId())
                .serviceName(target.getServiceName())
                .environment(target.getEnvironment())
                .baseUrl(target.getBaseUrl())
                .healthPath(target.getHealthPath())
                .agentId(target.getAgentId())
                .enabled(target.isEnabled())
                .intervalSeconds(target.getIntervalSeconds())
                .timeoutMs(target.getTimeoutMs())
                .failureThreshold(target.getFailureThreshold())
                .slowThresholdMs(target.getSlowThresholdMs())
                .build();
    }
}
