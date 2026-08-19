package cn.bugstack.ai.domain.ops.service;

import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.model.ServiceHealthStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpActuatorServiceProbeTest {

    @Test
    void classifiesHealthyActuatorResponseAsUp() {
        assertThat(HttpActuatorServiceProbe.classify(200, "{\"status\":\"UP\"}", 120, 1500))
                .isEqualTo(ServiceHealthStatus.UP);
    }

    @Test
    void classifiesSlowOrNonUpResponseAsDegraded() {
        assertThat(HttpActuatorServiceProbe.classify(200, "{\"status\":\"UP\"}", 1600, 1500))
                .isEqualTo(ServiceHealthStatus.DEGRADED);
        assertThat(HttpActuatorServiceProbe.classify(200, "{\"status\":\"OUT_OF_SERVICE\"}", 100, 1500))
                .isEqualTo(ServiceHealthStatus.DEGRADED);
        assertThat(HttpActuatorServiceProbe.classify(404, "", 100, 1500))
                .isEqualTo(ServiceHealthStatus.DEGRADED);
    }

    @Test
    void classifiesServerErrorOrDownPayloadAsDown() {
        assertThat(HttpActuatorServiceProbe.classify(503, "", 100, 1500))
                .isEqualTo(ServiceHealthStatus.DOWN);
        assertThat(HttpActuatorServiceProbe.classify(200, "{\"status\":\"DOWN\"}", 100, 1500))
                .isEqualTo(ServiceHealthStatus.DOWN);
    }

    @Test
    void buildsSafeProbeUriAndRejectsEmbeddedCredentials() {
        JavaServiceTarget target = JavaServiceTarget.builder()
                .baseUrl("https://orders.internal:8443/")
                .healthPath("actuator/health/readiness")
                .build();
        assertThat(HttpActuatorServiceProbe.buildProbeUri(target).toString())
                .isEqualTo("https://orders.internal:8443/actuator/health/readiness");

        target.setBaseUrl("https://user:secret@orders.internal");
        assertThatThrownBy(() -> HttpActuatorServiceProbe.buildProbeUri(target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户凭据");
    }
}
