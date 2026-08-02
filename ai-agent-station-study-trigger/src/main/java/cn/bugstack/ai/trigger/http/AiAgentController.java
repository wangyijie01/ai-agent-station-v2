package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAiAgentService;
import cn.bugstack.ai.api.dto.AiAgentResponseDTO;
import cn.bugstack.ai.api.dto.ArmoryAgentRequestDTO;
import cn.bugstack.ai.api.dto.ArmoryApiRequestDTO;
import cn.bugstack.ai.api.dto.AutoAgentRequestDTO;
import cn.bugstack.ai.api.dto.AgentSkillRouteRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.domain.agent.service.runtime.AgentRunService;
import cn.bugstack.ai.domain.agent.service.runtime.AgentSkillRegistry;
import cn.bugstack.ai.domain.agent.service.runtime.AgentSkillRouter;
import cn.bugstack.ai.domain.agent.service.runtime.ToolGovernanceService;
import cn.bugstack.ai.types.common.Constants;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 对外 HTTP/SSE 接口。
 *
 * <p>保留原有装配接口，并提供 V2 的 Skill 路由、运行查询、审计和取消能力。
 * 控制器只负责协议适配，状态流转和工具治理由领域服务完成。</p>
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * @author wangyijie01
 * @since 2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class AiAgentController implements IAiAgentService {

    @Resource
    private IAgentDispatchService agentDispatchService;

    @Resource
    private IArmoryService armoryService;

    @Resource
    private AgentSkillRegistry agentSkillRegistry;

    @Resource
    private AgentSkillRouter agentSkillRouter;

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private ToolGovernanceService toolGovernanceService;

    /**
     * 启动或恢复 Agent，并通过 SSE 持续返回带序号的运行事件。
     */
    @RequestMapping(value = "auto_agent", method = RequestMethod.POST)
    public ResponseBodyEmitter autoAgent(@RequestBody AutoAgentRequestDTO request, HttpServletResponse response) {
        log.info("AutoAgent流式执行请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 禁止中间层缓存流式事件，避免客户端收到批量延迟数据。
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);

            // 将传输对象收敛为领域命令，后续校验与状态管理均在领域层完成。
            ExecuteCommandEntity executeCommandEntity = ExecuteCommandEntity.builder()
                    .aiAgentId(request.getAiAgentId())
                    .message(request.getMessage())
                    .sessionId(request.getSessionId())
                    .maxStep(request.getMaxStep())
                    .idempotencyKey(request.getIdempotencyKey())
                    .resumeRunId(request.getResumeRunId())
                    .requestedSkillIds(request.getRequestedSkillIds())
                    .approvedToolNames(request.getApprovedToolNames())
                    .maxToolCalls(request.getMaxToolCalls())
                    .build();

            agentDispatchService.dispatch(executeCommandEntity, emitter);

            return emitter;

        } catch (Exception e) {
            log.error("AutoAgent请求处理异常：{}", e.getMessage(), e);
            ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter();
            try {
                errorEmitter.send("请求处理异常：" + e.getMessage());
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("发送错误信息失败：{}", ex.getMessage(), ex);
            }
            return errorEmitter;
        }
    }

    /** 返回当前已加载且通过校验的文件型 Skills。 */
    @GetMapping("skills")
    public Response<?> listSkills() {
        return success(agentSkillRegistry.list());
    }

    /** 预览 Skill 路由结果，不创建实际运行。 */
    @PostMapping("skills/route")
    public Response<?> routeSkills(@RequestBody AgentSkillRouteRequestDTO request) {
        int limit = request.getLimit() == null ? 3 : request.getLimit();
        return success(agentSkillRouter.route(request.getMessage(), request.getRequestedSkillIds(), limit));
    }

    /** 查询运行快照；runId 不存在时返回明确的业务错误。 */
    @GetMapping("runs/{runId}")
    public Response<?> queryRun(@PathVariable String runId) {
        return agentRunService.find(runId)
                .<Response<?>>map(this::success)
                .orElseGet(() -> failure(ResponseCode.ILLEGAL_PARAMETER, "runId 不存在"));
    }

    /** 查询可用于回放的有序运行事件。 */
    @GetMapping("runs/{runId}/events")
    public Response<?> queryRunEvents(@PathVariable String runId) {
        return success(agentRunService.events(runId));
    }

    /** 查询恢复运行所需的阶段检查点。 */
    @GetMapping("runs/{runId}/checkpoints")
    public Response<?> queryRunCheckpoints(@PathVariable String runId) {
        return success(agentRunService.checkpoints(runId));
    }

    /** 返回基于运行结果和治理记录生成的质量评估。 */
    @GetMapping("runs/{runId}/evaluation")
    public Response<?> evaluateRun(@PathVariable String runId) {
        return success(agentRunService.evaluate(runId));
    }

    /** 查询工具放行、拒绝、重试和熔断的脱敏审计记录。 */
    @GetMapping("runs/{runId}/tool-audits")
    public Response<?> queryToolAudits(@PathVariable String runId) {
        return success(toolGovernanceService.audits(runId));
    }

    /** 将可取消的运行迁移到 CANCELED 终态。 */
    @PostMapping("runs/{runId}/cancel")
    public Response<?> cancelRun(@PathVariable String runId) {
        return success(agentRunService.cancel(runId));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("处理成功")
                .data(data)
                .build();
    }

    private Response<?> failure(ResponseCode code, String message) {
        return Response.builder()
                .code(code.getCode())
                .info(message)
                .data(null)
                .build();
    }

    @RequestMapping(value = "armory_agent", method = RequestMethod.POST)
    @Override
    public Response<Boolean> armoryAgent(@RequestBody ArmoryAgentRequestDTO request) {
        log.info("装配智能体请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
                log.warn("装配智能体请求参数无效：agentId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("agentId不能为空")
                        .data(false)
                        .build();
            }
            
            // 调用装配服务
            armoryService.acceptArmoryAgent(request.getAgentId());
            
            log.info("装配智能体成功，agentId：{}", request.getAgentId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配智能体失败，agentId：{}，错误信息：{}", 
                    request != null ? request.getAgentId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "query_available_agents", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentResponseDTO>> queryAvailableAgents() {
        log.info("查询可用智能体列表请求开始");

        try {
            // 调用装配服务查询可用智能体
            List<AiAgentVO> aiAgentVOList = armoryService.queryAvailableAgents();
            
            // 转换为响应DTO
            List<AiAgentResponseDTO> responseList = new ArrayList<>();
            for (AiAgentVO aiAgentVO : aiAgentVOList) {
                AiAgentResponseDTO responseDTO = AiAgentResponseDTO.builder()
                        .agentId(aiAgentVO.getAgentId())
                        .agentName(aiAgentVO.getAgentName())
                        .description(aiAgentVO.getDescription())
                        .channel(aiAgentVO.getChannel())
                        .strategy(aiAgentVO.getStrategy())
                        .status(aiAgentVO.getStatus())
                        .build();
                responseList.add(responseDTO);
            }
            
            log.info("查询可用智能体列表成功，共{}个智能体", responseList.size());
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("查询成功")
                    .data(responseList)
                    .build();
                    
        } catch (Exception e) {
            log.error("查询可用智能体列表失败，错误信息：{}", e.getMessage(), e);
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询失败：" + e.getMessage())
                    .data(new ArrayList<>())
                    .build();
        }
    }

    @RequestMapping(value = "armory_api", method = RequestMethod.POST)
    @Override
    public Response<Boolean> armoryApi(@RequestBody ArmoryApiRequestDTO request) {
        log.info("装配API请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getApiId() == null || request.getApiId().trim().isEmpty()) {
                log.warn("装配API请求参数无效：apiId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("apiId不能为空")
                        .data(false)
                        .build();
            }
            
            // 调用装配服务
            armoryService.acceptArmoryAgentClientModelApi(request.getApiId());
            
            log.info("装配API成功，apiId：{}", request.getApiId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配API失败，apiId：{}，错误信息：{}", 
                    request != null ? request.getApiId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

}
