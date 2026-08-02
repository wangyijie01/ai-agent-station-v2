package cn.bugstack.ai.domain.agent.service.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Spring AI ToolCallback 装饰器，把治理放在服务端真实调用边界，而非仅靠 Prompt。
 */
final class GovernedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolGovernanceService governanceService;

    GovernedToolCallback(ToolCallback delegate, ToolGovernanceService governanceService) {
        this.delegate = delegate;
        this.governanceService = governanceService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return governanceService.execute(delegate, toolInput, new ToolContext(java.util.Map.of()));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return governanceService.execute(delegate, toolInput, toolContext);
    }
}
