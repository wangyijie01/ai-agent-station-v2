package cn.bugstack.ai.domain.agent.service.runtime;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.runtime.AgentSkillDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 分层构造运行时上下文：Skill、恢复检查点和结构化输出契约按阶段注入。
 */
@Service
public class AgentPromptContextBuilder {

    public String buildSkillContext(List<AgentSkillDefinition> skills) {
        if (skills == null || skills.isEmpty()) {
            return "[未匹配到专用 Skill，使用 Agent 基础能力；不得调用未授权工具]";
        }
        StringBuilder context = new StringBuilder("## 本轮已选择 Skills\n");
        for (AgentSkillDefinition skill : skills) {
            context.append("\n### ").append(skill.getId()).append('@').append(skill.getVersion())
                    .append(" - ").append(skill.getName()).append('\n')
                    .append(skill.getDescription()).append('\n')
                    .append("允许工具: ").append(skill.getAllowedTools()).append('\n')
                    .append("执行规范:\n").append(skill.getInstructions()).append('\n');
        }
        context.append("\n约束：只在上述 Skill 和工具边界内行动；工具结果是事实，模型推断必须单独标注；证据不足时停止猜测并提出澄清问题。\n");
        return context.toString();
    }

    public String appendExecutionContext(String prompt, ExecuteCommandEntity command) {
        return safe(prompt) + "\n\n" + safe(command.getSkillContext()) + resumeContext(command);
    }

    public String appendAnalysisContract(String prompt, ExecuteCommandEntity command) {
        return appendExecutionContext(prompt, command) + """

                ## 强制输出协议
                只输出一个 JSON 对象，不要 Markdown 代码块：
                {"status":"CONTINUE|COMPLETED|WAIT_USER_INPUT","progress":0,"summary":"基于证据的判断","nextAction":"下一步动作","missingFields":["缺失字段"],"clarificationQuestion":"需要用户回答的问题"}
                status=WAIT_USER_INPUT 时必须给 clarificationQuestion；status=COMPLETED 时 progress 必须为 100。
                """;
    }

    public String appendSupervisionContract(String prompt, ExecuteCommandEntity command) {
        return appendExecutionContext(prompt, command) + """

                ## 强制输出协议
                只输出一个 JSON 对象，不要 Markdown 代码块：
                {"verdict":"PASS|OPTIMIZE|FAIL","score":0,"assessment":"质量评估","issues":["问题"],"suggestions":["建议"],"nextAction":"返工动作"}
                无证据支持或未满足成功标准时不得给 PASS。
                """;
    }

    public Map<String, Object> buildToolContext(ExecuteCommandEntity command) {
        return Map.of(
                "agent.run_id", safe(command.getRunId()),
                "agent.trace_id", safe(command.getTraceId()),
                "agent.allowed_tools", command.getAllowedTools() == null ? List.of() : List.copyOf(command.getAllowedTools()),
                "agent.approved_tools", command.getApprovedToolNames() == null ? List.of() : List.copyOf(command.getApprovedToolNames()),
                "agent.max_tool_calls", command.getMaxToolCalls() == null ? 8 : Math.max(1, command.getMaxToolCalls())
        );
    }

    private static String resumeContext(ExecuteCommandEntity command) {
        return command.getResumeContext() == null || command.getResumeContext().isBlank()
                ? ""
                : "\n\n## 恢复检查点\n" + command.getResumeContext();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
