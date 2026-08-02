package cn.bugstack.ai.domain.agent.service.execute.flow;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.types.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 流程执行策略
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/5 09:56
 */
@Slf4j
@Service("flowAgentExecuteStrategy")
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultFlowAgentExecuteStrategyFactory defaultFlowAgentExecuteStrategyFactory;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultFlowAgentExecuteStrategyFactory.armoryStrategyHandler();
        
        // 创建动态上下文并初始化必要字段
        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultFlowAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.setCommand(executeCommandEntity);
        dynamicContext.setMaxStep(executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 4);
        dynamicContext.setExecutionHistory(new StringBuilder());
        dynamicContext.setCurrentTask(executeCommandEntity.getMessage());
        dynamicContext.setValue("emitter", emitter);
        
        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("流程执行结果:{}", apply);
    }

}
