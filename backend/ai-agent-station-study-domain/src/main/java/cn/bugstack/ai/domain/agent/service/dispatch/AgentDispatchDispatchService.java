package cn.bugstack.ai.domain.agent.service.dispatch;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.runtime.AgentRunService;
import cn.bugstack.ai.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/6 06:55
 */
@Slf4j
@Service
public class AgentDispatchDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Resource
    private IAgentRepository repository;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private AgentRunService agentRunService;

    @Override
    public void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        AiAgentVO aiAgentVO = repository.queryAiAgentByAgentId(requestParameter.getAiAgentId());

        String strategy = aiAgentVO.getStrategy();
        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (null == executeStrategy) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        var startResult = agentRunService.start(requestParameter, strategy);
        if (startResult.isReplay()) {
            agentRunService.replay(startResult.getRun().getRunId(), emitter);
            emitter.complete();
            return;
        }

        agentRunService.publishStarted(requestParameter, emitter, startResult.isResumed());

        Future<?> future = threadPoolExecutor.submit(() -> {
            try {
                executeStrategy.execute(requestParameter, emitter);
                agentRunService.complete(requestParameter, emitter);
            } catch (Exception e) {
                log.error("AutoAgent执行异常：{}", e.getMessage(), e);
                if (agentRunService.find(requestParameter.getRunId())
                        .map(run -> run.getStatus() != cn.bugstack.ai.domain.agent.model.runtime.AgentRunStatus.CANCELED)
                        .orElse(false)) {
                    agentRunService.fail(requestParameter, emitter, e);
                }
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成流式输出失败：{}", e.getMessage(), e);
                }
            }
        });
        agentRunService.attachFuture(requestParameter.getRunId(), future);

    }

}
