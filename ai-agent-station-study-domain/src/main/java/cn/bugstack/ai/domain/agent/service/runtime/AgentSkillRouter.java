package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.AgentSkillDefinition;
import cn.bugstack.ai.domain.agent.model.runtime.SkillMatch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 可解释的两段式 Skill 路由：显式选择优先，未显式选择时用场景/触发词召回。
 */
@Service
public class AgentSkillRouter {

    private final AgentSkillRegistry registry;

    public AgentSkillRouter(AgentSkillRegistry registry) {
        this.registry = registry;
    }

    public List<SkillMatch> route(String message, Collection<String> requestedSkillIds, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        Set<String> requested = new LinkedHashSet<>();
        if (requestedSkillIds != null) {
            requestedSkillIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> id.toLowerCase(Locale.ROOT))
                    .forEach(requested::add);
        }

        List<SkillMatch> matches = new ArrayList<>();
        for (AgentSkillDefinition skill : registry.list()) {
            List<String> reasons = new ArrayList<>();
            double score = skill.getPriority() / 10_000.0;
            if (requested.contains(skill.getId())) {
                score += 1.0;
                reasons.add("explicit:" + skill.getId());
            } else {
                for (String trigger : skill.getTriggerWords()) {
                    if (!trigger.isBlank() && normalized.contains(trigger.toLowerCase(Locale.ROOT))) {
                        score += 0.22;
                        reasons.add("trigger:" + trigger);
                    }
                }
                for (String scene : skill.getScenes()) {
                    if (!scene.isBlank() && normalized.contains(scene.toLowerCase(Locale.ROOT))) {
                        score += 0.12;
                        reasons.add("scene:" + scene);
                    }
                }
            }
            if (!reasons.isEmpty()) {
                matches.add(SkillMatch.builder()
                        .skill(skill)
                        .score(Math.min(score, 1.0))
                        .reasons(reasons)
                        .build());
            }
        }

        matches.sort(Comparator.comparingDouble(SkillMatch::getScore).reversed()
                .thenComparing(match -> match.getSkill().getPriority(), Comparator.reverseOrder())
                .thenComparing(match -> match.getSkill().getId()));
        return matches.stream().limit(safeLimit).toList();
    }
}
