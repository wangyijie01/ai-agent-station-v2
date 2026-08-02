package cn.bugstack.ai.domain.agent.service.execute.fixed;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.runtime.AgentPromptContextBuilder;
import cn.bugstack.ai.domain.agent.service.runtime.AgentRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 固定执行策略
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/13 15:14
 */
@Slf4j
@Service("fixedAgentExecuteStrategy")
public class FixedAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private AgentPromptContextBuilder promptContextBuilder;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    public void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        // 1. 获取配置客户端
        List<AiAgentClientFlowConfigVO> aiAgentClientList = repository.queryAiAgentClientsByAgentId(requestParameter.getAiAgentId());

        if (aiAgentClientList == null || aiAgentClientList.isEmpty()) {
            throw new IllegalStateException("Fixed Agent 未配置执行 Client");
        }

        // 2. 循环执行客户端
        String content = "";

        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            ChatClient chatClient = getChatClientByClientId(config.getClientId());

            String prompt = promptContextBuilder.appendExecutionContext(
                    requestParameter.getMessage() + "，" + content, requestParameter);
            content = chatClient.prompt(prompt)
                    .toolContext(promptContextBuilder.buildToolContext(requestParameter))
                    .system(s -> s.param("current_date", LocalDate.now().toString()))
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                    .call().content();

            log.info("智能体对话进行，客户端ID {}", requestParameter.getAiAgentId());
        }

        log.info("智能体对话请求，结果 {} {}", requestParameter.getAiAgentId(), content);
        
        // 发送最终结果通知（确保 content 不为空）
        if (content != null && !content.trim().isEmpty()) {
            sendFinalResult(emitter, content, requestParameter);
        }

        agentRunService.checkpoint(requestParameter, "FIXED_COMPLETED", aiAgentClientList.size(),
                content, "完成");
    }

    private ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    private <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
    
    /**
     * 发送最终结果到流式输出
     */
    private void sendFinalResult(ResponseBodyEmitter emitter, String content, ExecuteCommandEntity command) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(content, command.getSessionId());
        agentRunService.publish(command, emitter, result);
        log.info("✅ 已发送最终结果");
    }

}
