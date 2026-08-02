package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.runtime.AgentAnalysisDecision;
import cn.bugstack.ai.domain.agent.model.runtime.AgentSupervisionDecision;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化输出解析器。JSON 是主路径，旧中文模板作为兼容兜底并显式打标。
 */
@Service
public class AgentDecisionParser {

    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern SCORE_PATTERN = Pattern.compile("(?:质量评分|score)\\s*[:：]?\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);

    public AgentAnalysisDecision parseAnalysis(String raw) {
        JSONObject json = parseJsonObject(raw);
        if (json != null) {
            String statusValue = value(json, "status", "taskStatus", "任务状态");
            AgentAnalysisDecision.Status status = parseAnalysisStatus(statusValue);
            int progress = clamp(json.getIntValue("progress", status == AgentAnalysisDecision.Status.COMPLETED ? 100 : 0));
            return AgentAnalysisDecision.builder()
                    .status(status)
                    .progress(progress)
                    .summary(value(json, "summary", "assessment", "任务状态分析"))
                    .nextAction(value(json, "nextAction", "next_action", "下一步策略"))
                    .clarificationQuestion(value(json, "clarificationQuestion", "clarification_question", "澄清问题"))
                    .missingFields(stringList(json, "missingFields", "missing_fields", "缺失字段"))
                    .fallbackParsed(false)
                    .build();
        }

        String safe = raw == null ? "" : raw;
        String upper = safe.toUpperCase(Locale.ROOT);
        AgentAnalysisDecision.Status status;
        if (upper.contains("WAIT_USER_INPUT") || upper.contains("PENDING_USER_INPUT")
                || safe.contains("需要用户补充") || safe.contains("信息不足，请")) {
            status = AgentAnalysisDecision.Status.WAIT_USER_INPUT;
        } else if (upper.contains("COMPLETED") || safe.contains("完成度评估: 100%")
                || safe.contains("完成度评估：100%")) {
            status = AgentAnalysisDecision.Status.COMPLETED;
        } else {
            status = AgentAnalysisDecision.Status.CONTINUE;
        }
        return AgentAnalysisDecision.builder()
                .status(status)
                .progress(extractNumber(PERCENT_PATTERN, safe, status == AgentAnalysisDecision.Status.COMPLETED ? 100 : 0))
                .summary(safe)
                .clarificationQuestion(status == AgentAnalysisDecision.Status.WAIT_USER_INPUT ? safe : null)
                .fallbackParsed(true)
                .build();
    }

    public AgentSupervisionDecision parseSupervision(String raw) {
        JSONObject json = parseJsonObject(raw);
        if (json != null) {
            String verdictValue = value(json, "verdict", "status", "是否通过");
            return AgentSupervisionDecision.builder()
                    .verdict(parseVerdict(verdictValue))
                    .score(clamp(json.getIntValue("score", 0)))
                    .assessment(value(json, "assessment", "质量评估"))
                    .issues(stringList(json, "issues", "问题识别"))
                    .suggestions(stringList(json, "suggestions", "改进建议"))
                    .nextAction(value(json, "nextAction", "next_action", "下一步"))
                    .fallbackParsed(false)
                    .build();
        }

        String safe = raw == null ? "" : raw;
        String upper = safe.toUpperCase(Locale.ROOT);
        AgentSupervisionDecision.Verdict verdict = upper.contains("是否通过: PASS") || upper.contains("是否通过：PASS")
                ? AgentSupervisionDecision.Verdict.PASS
                : upper.contains("是否通过: FAIL") || upper.contains("是否通过：FAIL")
                ? AgentSupervisionDecision.Verdict.FAIL
                : AgentSupervisionDecision.Verdict.OPTIMIZE;
        return AgentSupervisionDecision.builder()
                .verdict(verdict)
                .score(extractNumber(SCORE_PATTERN, safe, verdict == AgentSupervisionDecision.Verdict.PASS ? 80 : 0))
                .assessment(safe)
                .fallbackParsed(true)
                .build();
    }

    private static JSONObject parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        int first = candidate.indexOf('{');
        int last = candidate.lastIndexOf('}');
        if (first < 0 || last <= first) {
            return null;
        }
        try {
            return JSON.parseObject(candidate.substring(first, last + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AgentAnalysisDecision.Status parseAnalysisStatus(String value) {
        if (value == null) {
            return AgentAnalysisDecision.Status.CONTINUE;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("WAIT") || upper.contains("PENDING") || value.contains("补充")) {
            return AgentAnalysisDecision.Status.WAIT_USER_INPUT;
        }
        return upper.contains("COMPLETE") ? AgentAnalysisDecision.Status.COMPLETED : AgentAnalysisDecision.Status.CONTINUE;
    }

    private static AgentSupervisionDecision.Verdict parseVerdict(String value) {
        if (value == null) {
            return AgentSupervisionDecision.Verdict.OPTIMIZE;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("PASS")) {
            return AgentSupervisionDecision.Verdict.PASS;
        }
        if (upper.contains("FAIL")) {
            return AgentSupervisionDecision.Verdict.FAIL;
        }
        return AgentSupervisionDecision.Verdict.OPTIMIZE;
    }

    private static String value(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> stringList(JSONObject object, String... keys) {
        for (String key : keys) {
            JSONArray array = object.getJSONArray(key);
            if (array != null) {
                return array.toJavaList(String.class);
            }
            String scalar = object.getString(key);
            if (scalar != null && !scalar.isBlank()) {
                List<String> values = new ArrayList<>();
                for (String item : scalar.split("[,，;；]")) {
                    if (!item.isBlank()) {
                        values.add(item.trim());
                    }
                }
                return values;
            }
        }
        return List.of();
    }

    private static int extractNumber(Pattern pattern, String value, int defaultValue) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? clamp(Integer.parseInt(matcher.group(1))) : defaultValue;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
