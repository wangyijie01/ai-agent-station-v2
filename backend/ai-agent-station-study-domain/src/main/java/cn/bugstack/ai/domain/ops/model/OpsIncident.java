package cn.bugstack.ai.domain.ops.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 由连续异常探测聚合形成的可处置运维事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsIncident {

    private String incidentId;
    private String serviceId;
    private String serviceName;
    private String environment;
    private OpsIncidentStatus status;
    private OpsIncidentSeverity severity;
    private String summary;

    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    private String analysisPrompt;
    private String linkedRunId;
    private int occurrenceCount;
    private long openedAt;
    private long updatedAt;
    private Long acknowledgedAt;
    private Long resolvedAt;
}
