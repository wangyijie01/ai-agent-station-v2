package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 触发运维事件 Agent 分析的受控参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsAnalyzeRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 64)
    private String agentId;

    @Min(1)
    @Max(20)
    private Integer maxStep;

    @Min(0)
    @Max(50)
    private Integer maxToolCalls;

    /** 生产环境应由审批系统写入，不能直接信任普通终端用户。 */
    @Builder.Default
    @Size(max = 20)
    private List<@Size(max = 128) String> approvedToolNames = new ArrayList<>();
}
