package cn.bugstack.ai.domain.ops.service;

import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.model.ServiceHealthStatus;
import cn.bugstack.ai.domain.ops.model.ServiceProbeResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 使用 JDK HttpClient 探测 Spring Boot Actuator 健康端点。 */
@Component
public class HttpActuatorServiceProbe implements JavaServiceProbe {

    private static final int MAX_EVIDENCE_LENGTH = 512;
    private static final int MAX_RESPONSE_BYTES = 4_096;

    private final HttpClient httpClient;

    public HttpActuatorServiceProbe() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    HttpActuatorServiceProbe(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ServiceProbeResult probe(JavaServiceTarget target) {
        long start = System.nanoTime();
        long observedAt = System.currentTimeMillis();
        URI uri = buildProbeUri(target);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(target.getTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                body = new String(bytes, 0, Math.min(bytes.length, MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    body += "...";
                }
            }
            long latencyMs = elapsedMillis(start);
            ServiceHealthStatus status = classify(response.statusCode(), body, latencyMs,
                    target.getSlowThresholdMs());
            return ServiceProbeResult.builder()
                    .serviceId(target.getServiceId())
                    .status(status)
                    .httpStatus(response.statusCode())
                    .latencyMs(latencyMs)
                    .observedAt(observedAt)
                    .summary(summary(status, response.statusCode(), latencyMs))
                    .evidence(sanitize(body))
                    .build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(target, observedAt, start, "探测线程被中断");
        } catch (Exception exception) {
            return failure(target, observedAt, start,
                    exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage()));
        }
    }

    static URI buildProbeUri(JavaServiceTarget target) {
        URI base = URI.create(target.getBaseUrl());
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalArgumentException("仅支持 HTTP/HTTPS 服务探测");
        }
        if (base.getUserInfo() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("服务地址不能包含用户凭据或 fragment");
        }
        String path = target.getHealthPath().startsWith("/")
                ? target.getHealthPath() : "/" + target.getHealthPath();
        String normalizedBase = target.getBaseUrl().endsWith("/")
                ? target.getBaseUrl().substring(0, target.getBaseUrl().length() - 1)
                : target.getBaseUrl();
        return URI.create(normalizedBase + path);
    }

    static ServiceHealthStatus classify(int httpStatus, String body, long latencyMs, long slowThresholdMs) {
        if (httpStatus >= 500) {
            return ServiceHealthStatus.DOWN;
        }
        if (httpStatus >= 400) {
            return ServiceHealthStatus.DEGRADED;
        }
        String actuatorStatus = actuatorStatus(body);
        if (actuatorStatus != null && !"UP".equalsIgnoreCase(actuatorStatus)) {
            return "DOWN".equalsIgnoreCase(actuatorStatus)
                    ? ServiceHealthStatus.DOWN : ServiceHealthStatus.DEGRADED;
        }
        return latencyMs > slowThresholdMs ? ServiceHealthStatus.DEGRADED : ServiceHealthStatus.UP;
    }

    private static String actuatorStatus(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(body);
            return object == null ? null : object.getString("status");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ServiceProbeResult failure(JavaServiceTarget target,
                                              long observedAt,
                                              long start,
                                              String evidence) {
        return ServiceProbeResult.builder()
                .serviceId(target.getServiceId())
                .status(ServiceHealthStatus.DOWN)
                .latencyMs(elapsedMillis(start))
                .observedAt(observedAt)
                .summary("健康探测失败")
                .evidence(sanitize(evidence))
                .build();
    }

    private static long elapsedMillis(long start) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    private static String summary(ServiceHealthStatus status, int httpStatus, long latencyMs) {
        return "Actuator 状态=" + status + "，HTTP=" + httpStatus + "，延迟=" + latencyMs + "ms";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return compact.length() <= MAX_EVIDENCE_LENGTH
                ? compact : compact.substring(0, MAX_EVIDENCE_LENGTH) + "...";
    }

    private static String safeMessage(String value) {
        return value == null || value.isBlank() ? "无详细错误" : value;
    }
}
