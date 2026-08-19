package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.dto.JavaServiceTargetRequestDTO;
import cn.bugstack.ai.api.dto.OpsAnalyzeRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.model.OpsIncident;
import cn.bugstack.ai.domain.ops.model.OpsIncidentStatus;
import cn.bugstack.ai.domain.ops.service.OpsSupervisionService;
import cn.bugstack.ai.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/** Java 服务监督、事件管理与 Agent 分析闭环 API。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops")
public class OpsSupervisionController {

    private final OpsSupervisionService supervisionService;
    private final IAgentDispatchService agentDispatchService;

    @GetMapping("/services")
    public Response<?> listServices() {
        return success(supervisionService.listTargets());
    }

    @PostMapping("/services")
    public Response<?> saveService(@Valid @RequestBody JavaServiceTargetRequestDTO request) {
        return success(supervisionService.register(toTarget(request)));
    }

    @DeleteMapping("/services/{serviceId}")
    public Response<?> removeService(@PathVariable String serviceId) {
        return success(supervisionService.remove(serviceId));
    }

    @PostMapping("/services/{serviceId}/probe")
    public Response<?> probeService(@PathVariable String serviceId) {
        return success(supervisionService.check(serviceId));
    }

    @PostMapping("/services/probe-all")
    public Response<?> probeAllServices() {
        return success(supervisionService.checkAll());
    }

    @GetMapping("/snapshots")
    public Response<?> snapshots() {
        return success(supervisionService.snapshots());
    }

    @GetMapping("/incidents")
    public Response<?> incidents(@RequestParam(required = false) OpsIncidentStatus status) {
        return success(supervisionService.incidents(status));
    }

    @GetMapping("/incidents/{incidentId}")
    public Response<?> incident(@PathVariable String incidentId) {
        return success(requiredIncident(incidentId));
    }

    @PostMapping("/incidents/{incidentId}/acknowledge")
    public Response<?> acknowledge(@PathVariable String incidentId) {
        return success(supervisionService.acknowledge(incidentId));
    }

    @PostMapping("/incidents/{incidentId}/resolve")
    public Response<?> resolve(@PathVariable String incidentId) {
        return success(supervisionService.resolve(incidentId));
    }

    @PostMapping(value = "/incidents/{incidentId}/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter analyze(@PathVariable String incidentId,
                                       @Valid @RequestBody(required = false) OpsAnalyzeRequestDTO input,
                                       HttpServletResponse response) throws Exception {
        OpsAnalyzeRequestDTO request = input == null ? new OpsAnalyzeRequestDTO() : input;
        OpsIncident incident = requiredIncident(incidentId);
        JavaServiceTarget target = supervisionService.findTarget(incident.getServiceId())
                .orElseThrow(() -> new IllegalStateException("事件关联的监督目标不存在"));
        String agentId = firstNotBlank(request.getAgentId(), target.getAgentId());
        if (agentId == null) {
            throw new IllegalArgumentException("请为监督目标配置 agentId，或在分析请求中传入 agentId");
        }

        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        ExecuteCommandEntity command = ExecuteCommandEntity.builder()
                .aiAgentId(agentId)
                .message(incident.getAnalysisPrompt())
                .sessionId("ops:" + incident.getServiceId())
                .idempotencyKey("ops-incident:" + incidentId)
                .requestedSkillIds(List.of("monitoring-diagnosis", "log-root-cause"))
                .approvedToolNames(request.getApprovedToolNames() == null ? List.of() : request.getApprovedToolNames())
                .maxStep(request.getMaxStep() == null ? 6 : request.getMaxStep())
                .maxToolCalls(request.getMaxToolCalls() == null ? 8 : request.getMaxToolCalls())
                .build();
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);
        agentDispatchService.dispatch(command, emitter);
        supervisionService.linkRun(incidentId, command.getRunId());
        response.setHeader("X-Agent-Run-Id", command.getRunId());
        return emitter;
    }

    private OpsIncident requiredIncident(String incidentId) {
        return supervisionService.findIncident(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("运维事件不存在: " + incidentId));
    }

    private static JavaServiceTarget toTarget(JavaServiceTargetRequestDTO request) {
        return JavaServiceTarget.builder()
                .serviceId(request.getServiceId())
                .serviceName(request.getServiceName())
                .environment(request.getEnvironment())
                .baseUrl(request.getBaseUrl())
                .healthPath(firstNotBlank(request.getHealthPath(), "/actuator/health"))
                .agentId(request.getAgentId())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .intervalSeconds(request.getIntervalSeconds() == null ? 30 : request.getIntervalSeconds())
                .timeoutMs(request.getTimeoutMs() == null ? 3_000 : request.getTimeoutMs())
                .failureThreshold(request.getFailureThreshold() == null ? 3 : request.getFailureThreshold())
                .slowThresholdMs(request.getSlowThresholdMs() == null ? 1_500 : request.getSlowThresholdMs())
                .build();
    }

    private static String firstNotBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
