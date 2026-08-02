package cn.bugstack.ai.types.design.framework.tree;

/** 根据请求和动态上下文选择下一策略节点。 */
@FunctionalInterface
public interface StrategyMapper<T, D, R> {

    StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext) throws Exception;
}
