package cn.bugstack.ai.types.design.framework.tree;

/**
 * 项目内的策略树处理器契约。
 *
 * <p>保留最小抽象，避免运行时依赖携带第三方日志实现的 fat jar。</p>
 */
@FunctionalInterface
public interface StrategyHandler<T, D, R> {

    StrategyHandler<?, ?, ?> DEFAULT = (requestParameter, dynamicContext) -> null;

    R apply(T requestParameter, D dynamicContext) throws Exception;

    @SuppressWarnings("unchecked")
    static <T, D, R> StrategyHandler<T, D, R> defaultHandler() {
        return (StrategyHandler<T, D, R>) DEFAULT;
    }
}
