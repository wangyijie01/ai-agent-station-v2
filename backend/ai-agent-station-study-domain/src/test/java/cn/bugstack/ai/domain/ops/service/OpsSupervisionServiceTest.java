package cn.bugstack.ai.domain.ops.service;

import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.model.OpsIncident;
import cn.bugstack.ai.domain.ops.model.OpsIncidentSeverity;
import cn.bugstack.ai.domain.ops.model.OpsIncidentStatus;
import cn.bugstack.ai.domain.ops.model.ServiceHealthStatus;
import cn.bugstack.ai.domain.ops.model.ServiceProbeResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsSupervisionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldSuppressTransientFailureAndCloseIncidentAfterRecovery() {
        Queue<ServiceProbeResult> results = new ArrayDeque<>(List.of(
                result(ServiceHealthStatus.DOWN, 503, "连接池耗尽"),
                result(ServiceHealthStatus.DOWN, 503, "连接池耗尽"),
                result(ServiceHealthStatus.DOWN, 503, "连接池耗尽"),
                result(ServiceHealthStatus.UP, 200, "status=UP")
        ));
        OpsSupervisionService service = new OpsSupervisionService(target -> results.remove(), CLOCK);
        JavaServiceTarget target = service.register(target("order-service", 2));

        ServiceProbeResult first = service.check(target.getServiceId());
        assertThat(first.getConsecutiveFailures()).isEqualTo(1);
        assertThat(service.incidents(null)).isEmpty();

        ServiceProbeResult second = service.check(target.getServiceId());
        assertThat(second.getConsecutiveFailures()).isEqualTo(2);
        OpsIncident opened = service.incidents(OpsIncidentStatus.OPEN).get(0);
        assertThat(opened.getSeverity()).isEqualTo(OpsIncidentSeverity.CRITICAL);
        assertThat(opened.getAnalysisPrompt())
                .contains("order-service", "monitoring-diagnosis", "log-root-cause")
                .contains("事实、推断和处置建议");

        service.check(target.getServiceId());
        OpsIncident updated = service.findIncident(opened.getIncidentId()).orElseThrow();
        assertThat(updated.getOccurrenceCount()).isEqualTo(2);
        assertThat(updated.getEvidence()).hasSize(2);

        service.check(target.getServiceId());
        OpsIncident resolved = service.findIncident(opened.getIncidentId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(OpsIncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldAcknowledgeLinkRunAndProtectActiveIncident() {
        OpsSupervisionService service = new OpsSupervisionService(
                target -> result(ServiceHealthStatus.DEGRADED, 200, "响应超过阈值"), CLOCK);
        JavaServiceTarget target = service.register(target("payment-service", 1));
        service.check(target.getServiceId());
        OpsIncident incident = service.incidents(OpsIncidentStatus.OPEN).get(0);

        assertThat(incident.getSeverity()).isEqualTo(OpsIncidentSeverity.WARNING);
        assertThat(service.acknowledge(incident.getIncidentId()).getStatus())
                .isEqualTo(OpsIncidentStatus.ACKNOWLEDGED);
        assertThat(service.linkRun(incident.getIncidentId(), "run-001").getLinkedRunId())
                .isEqualTo("run-001");
        assertThatThrownBy(() -> service.remove(target.getServiceId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未解决事件");
        service.resolve(incident.getIncidentId());
        assertThat(service.remove(target.getServiceId())).isTrue();
    }

    @Test
    void shouldValidateMonitoringTarget() {
        OpsSupervisionService service = new OpsSupervisionService(
                target -> result(ServiceHealthStatus.UP, 200, "UP"), CLOCK);

        assertThatThrownBy(() -> service.register(JavaServiceTarget.builder()
                .serviceName("unsafe")
                .baseUrl("file:///etc/passwd")
                .intervalSeconds(30)
                .timeoutMs(1_000)
                .failureThreshold(1)
                .slowThresholdMs(500)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    private static JavaServiceTarget target(String name, int threshold) {
        return JavaServiceTarget.builder()
                .serviceId(name)
                .serviceName(name)
                .environment("prod")
                .baseUrl("http://127.0.0.1:8080")
                .healthPath("/actuator/health")
                .agentId("3")
                .intervalSeconds(30)
                .timeoutMs(1_000)
                .failureThreshold(threshold)
                .slowThresholdMs(500)
                .enabled(true)
                .build();
    }

    private static ServiceProbeResult result(ServiceHealthStatus status, Integer httpStatus, String evidence) {
        return ServiceProbeResult.builder()
                .status(status)
                .httpStatus(httpStatus)
                .latencyMs(status == ServiceHealthStatus.DEGRADED ? 1_800 : 40)
                .observedAt(CLOCK.millis())
                .summary("status=" + status)
                .evidence(evidence)
                .build();
    }
}
