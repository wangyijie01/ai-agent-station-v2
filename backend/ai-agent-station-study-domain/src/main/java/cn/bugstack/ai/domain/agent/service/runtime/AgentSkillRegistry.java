package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.AgentRiskLevel;
import cn.bugstack.ai.domain.agent.model.runtime.AgentSkillDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 从 classpath 下的 agent-skills/{skill}/SKILL.md 加载 Skill。
 *
 * <p>Skill 文件随应用版本发布，可在 CI 中审查和回滚；后续若切到数据库或 Skill Hub，
 * 只需替换 Registry 的数据源，路由与执行契约保持不变。</p>
 */
@Slf4j
@Service
public class AgentSkillRegistry {

    private static final String SKILL_PATTERN = "classpath*:agent-skills/*/SKILL.md";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private volatile Map<String, AgentSkillDefinition> skills = Map.of();

    @PostConstruct
    public void reload() {
        Map<String, AgentSkillDefinition> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = resolver.getResources(SKILL_PATTERN);
            for (Resource resource : resources) {
                AgentSkillDefinition definition = parse(resource);
                AgentSkillDefinition previous = loaded.putIfAbsent(definition.getId(), definition);
                if (previous != null) {
                    throw new IllegalStateException("重复的 Skill id: " + definition.getId());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载 Agent Skills 失败", e);
        }

        List<Map.Entry<String, AgentSkillDefinition>> ordered = new ArrayList<>(loaded.entrySet());
        ordered.sort(Map.Entry.<String, AgentSkillDefinition>comparingByValue(
                Comparator.comparing(AgentSkillDefinition::getPriority).reversed()
                        .thenComparing(AgentSkillDefinition::getId)));
        Map<String, AgentSkillDefinition> immutable = new LinkedHashMap<>();
        ordered.forEach(entry -> immutable.put(entry.getKey(), entry.getValue()));
        skills = Collections.unmodifiableMap(immutable);
        log.info("已加载 {} 个 Agent Skills: {}", skills.size(), skills.keySet());
    }

    public List<AgentSkillDefinition> list() {
        return List.copyOf(skills.values());
    }

    public Optional<AgentSkillDefinition> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public List<AgentSkillDefinition> resolve(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<AgentSkillDefinition> resolved = new ArrayList<>();
        for (String id : ids) {
            findById(id).ifPresent(resolved::add);
        }
        return resolved;
    }

    private AgentSkillDefinition parse(Resource resource) throws IOException {
        String content;
        try (var input = resource.getInputStream()) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException(resource.getDescription() + " 缺少 YAML front matter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException(resource.getDescription() + " front matter 未闭合");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        String[] lines = normalized.substring(4, end).split("\n");
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            metadata.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }

        String id = required(metadata, "id").toLowerCase(Locale.ROOT);
        String instructions = normalized.substring(end + 5).trim();
        if (instructions.isBlank()) {
            throw new IllegalArgumentException("Skill " + id + " 缺少执行说明");
        }

        return AgentSkillDefinition.builder()
                .id(id)
                .name(required(metadata, "name"))
                .version(metadata.getOrDefault("version", "1.0.0"))
                .description(required(metadata, "description"))
                .scenes(parseList(metadata.get("scenes")))
                .triggerWords(parseList(metadata.get("trigger-words")))
                .allowedTools(parseList(metadata.get("allowed-tools")))
                .riskLevel(parseRisk(metadata.get("risk-level")))
                .priority(parseInt(metadata.get("priority"), 0))
                .instructions(instructions)
                .resourcePath(resource.getDescription())
                .build();
    }

    private static String required(Map<String, String> metadata, String key) {
        String value = stripQuotes(metadata.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Skill 缺少必填字段: " + key);
        }
        return value;
    }

    private static List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            String parsed = stripQuotes(item.trim());
            if (parsed != null && !parsed.isBlank()) {
                values.add(parsed);
            }
        }
        return List.copyOf(values);
    }

    private static AgentRiskLevel parseRisk(String value) {
        if (value == null || value.isBlank()) {
            return AgentRiskLevel.READ_ONLY;
        }
        try {
            return AgentRiskLevel.valueOf(stripQuotes(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("未知 risk-level: " + value);
        }
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(stripQuotes(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
