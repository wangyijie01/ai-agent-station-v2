package cn.bugstack.ai.domain.agent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 路由结果，保留分数与命中原因，便于解释和离线评测。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMatch {

    private AgentSkillDefinition skill;
    private double score;

    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
