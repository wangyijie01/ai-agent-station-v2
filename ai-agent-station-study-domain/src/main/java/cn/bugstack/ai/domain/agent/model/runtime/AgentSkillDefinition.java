package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可版本化的 Agent Skill 定义。SKILL.md 是配置源，本对象是运行时契约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkillDefinition {

    private String id;
    private String name;
    private String version;
    private String description;

    @Builder.Default
    private List<String> scenes = new ArrayList<>();

    @Builder.Default
    private List<String> triggerWords = new ArrayList<>();

    @Builder.Default
    private List<String> allowedTools = new ArrayList<>();

    @Builder.Default
    private AgentRiskLevel riskLevel = AgentRiskLevel.READ_ONLY;

    @Builder.Default
    private Integer priority = 0;

    private String instructions;
    private String resourcePath;
}
